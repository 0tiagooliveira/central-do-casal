package com.centraldocasal.app.data

import android.content.Context
import com.centraldocasal.app.isGenericCatalogBase
import com.centraldocasal.app.domain.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SessionState(val uid: String = "", val name: String = "", val email: String = "", val local: Boolean = false, val household: String = "", val verified: Boolean = false)

/** A confirmed Firestore profile can supply the name missing from older Firebase Auth users. */
internal fun effectiveProfileName(documentName: String?, authName: String?): String =
    documentName?.trim()?.takeIf { it.isNotBlank() }
        ?: authName?.trim()?.takeIf { it.isNotBlank() }
        ?: "Meu perfil"

data class LiveLocation(val uid: String, val latitude: Double, val longitude: Double, val capturedAt: Long, val expiresAt: Long, val sessionEndsAt: Long) {
    fun isCurrent(now: Long): Boolean =
        now < expiresAt && now < sessionEndsAt &&
        capturedAt > 0 && now - capturedAt in -10_000L..89_999L &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
data class DataState(val records: List<Record> = emptyList(), val baskets: Map<String, Basket> = emptyMap(), val sync: String = "", val error: String? = null, val members: List<String> = emptyList(), val liveLocations: List<LiveLocation> = emptyList())

/** One repository owns persistence. Local data never silently migrate into a cloud household. */
class CentralRepository(context: Context) {
    private val planLocalMutex = Mutex()
    private val prefs = context.getSharedPreferences("central_local_v1", Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val mutableSession = MutableStateFlow(SessionState())
    val session = mutableSession.asStateFlow()
    private val mutableData = MutableStateFlow(DataState())
    val data = mutableData.asStateFlow()
    private var shared = emptyList<Record>()
    private var sharedLinks = emptyList<Record>()
    private var personal = emptyList<Record>()
    private var sharedStatus = "Carregando espaço"
    private var privateStatus = "Carregando área pessoal"
    private var profileListener: ListenerRegistration? = null
    private var listenerEpoch = 0
    private val access = SyncAccess()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val authListener = FirebaseAuth.AuthStateListener { provider ->
        if (mutableSession.value.local) return@AuthStateListener
        closeListeners(); shared = emptyList(); sharedLinks = emptyList(); personal = emptyList(); mutableData.value = DataState()
        val user = provider.currentUser
        prefs.edit().putString("activeUid", user?.uid.orEmpty()).apply()
        if (user == null) { mutableSession.value = SessionState(); return@AuthStateListener }
        mutableSession.value = SessionState(user.uid, user.displayName ?: "Meu perfil", user.email.orEmpty(), verified = user.isEmailVerified)
        watchProfile(user.uid)
    }
    private fun watchProfile(uid: String) {
        val epoch = listenerEpoch
        profileListener = db.collection("users").document(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (epoch != listenerEpoch || session.value.uid != uid) return@addSnapshotListener
            if (error != null) { fail("profile", error); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            val household = snap.getString("householdId").orEmpty()
            // A local profile write does not yet authorize reads of the new household.
            if (!confirmedProfile(snap.metadata.hasPendingWrites(), snap.metadata.isFromCache, prefs.getString("confirmedHousehold_$uid", null) == household)) return@addSnapshotListener
            if (!snap.metadata.isFromCache) { prefs.edit().putString("confirmedHousehold_$uid", household).apply(); access.confirm("profile") }
            val resolvedName = effectiveProfileName(snap.getString("displayName"), auth.currentUser?.displayName)
            if (resolvedName != mutableSession.value.name) {
                mutableSession.value = mutableSession.value.copy(name = resolvedName)
            }
            if (household != mutableSession.value.household || listeners.isEmpty()) {
                mutableSession.value = mutableSession.value.copy(household = household)
                subscribe(household, uid)
            } else publish()
        }
    }
    init {
        if (prefs.getString("activeUid", "") == "local") useLocal(prefs.getString("localName", "Meu perfil").orEmpty())
        auth.addAuthStateListener(authListener)
    }
    fun close() { closeListeners(); auth.removeAuthStateListener(authListener) }
    private fun closeListeners() { listenerEpoch++; profileListener?.remove(); profileListener = null; listeners.forEach { it.remove() }; listeners.clear(); access.reset() }
    private fun fail(source: String, error: Exception) {
        access.fail(source, if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) "O acesso a parte dos dados foi recusado. Toque em tentar novamente para verificar sua conta." else "A conexão foi interrompida. Seus dados salvos continuam no aparelho.")
        publish()
    }
    private fun publish() { mutableData.value = mutableData.value.copy(records = shared + sharedLinks + personal, error = access.error, sync = if (access.error != null) "Conexão precisa de atenção" else listOf(sharedStatus,privateStatus).distinct().joinToString(" • ")) }
    suspend fun retryConnection() {
        val user = auth.currentUser ?: return
        if (session.value.local) return
        user.getIdToken(true).await()
        closeListeners(); watchProfile(user.uid)
        mutableData.value = mutableData.value.copy(error = null, sync = "Reconectando")
    }
    private fun status(snap: QuerySnapshot) = when { snap.metadata.hasPendingWrites() -> "Alterações pendentes"; snap.metadata.isFromCache -> "Cache local • conexão não confirmada"; else -> "Sincronizado" }
    private fun subscribe(household: String, uid: String) {
        listeners.forEach { it.remove() }; listeners.clear(); shared = emptyList(); sharedLinks = emptyList(); personal = emptyList()
        access.retainProfile(); sharedStatus = if (household.isBlank()) "Sem espaço compartilhado" else "Carregando espaço"; privateStatus = "Carregando área pessoal"
        mutableData.value = DataState(sync = "Carregando")
        val epoch = listenerEpoch
        listeners += db.collection("users").document(uid).collection("records").addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (epoch != listenerEpoch || session.value.uid != uid) return@addSnapshotListener
            if (error != null) fail("personal", error)
            else if (snap != null) { if (!snap.metadata.isFromCache) access.confirm("personal"); personal = snap.documents.mapNotNull(::readRecord); privateStatus = status(snap); publish() }
        }
        if (household.isBlank()) { sharedStatus = "Sem espaço compartilhado"; publish(); return }
        val house = db.collection("households").document(household)
        listeners += house.addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (epoch != listenerEpoch || session.value.uid != uid || session.value.household != household) return@addSnapshotListener
            if (error != null) fail("house", error)
            else if (snap != null) { if (!snap.metadata.isFromCache) access.confirm("house"); @Suppress("UNCHECKED_CAST") val members = snap.get("members") as? List<String> ?: emptyList(); mutableData.value = mutableData.value.copy(members = members); publish() }
        }
        listeners += house.collection("records").addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (epoch != listenerEpoch || session.value.uid != uid || session.value.household != household) return@addSnapshotListener
            if (error != null) fail("shared", error)
            else if (snap != null) { if (!snap.metadata.isFromCache) access.confirm("shared"); shared = snap.documents.mapNotNull(::readRecord); sharedStatus = status(snap); publish() }
        }
        listeners += house.collection("catalogLinks").addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (epoch != listenerEpoch || session.value.uid != uid || session.value.household != household) return@addSnapshotListener
            if (error != null) fail("catalogLinks", error)
            else if (snap != null) { if (!snap.metadata.isFromCache) access.confirm("catalogLinks"); sharedLinks = snap.documents.mapNotNull(::readRecord); publish() }
        }
        listeners += house.collection("liveLocations").addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (epoch != listenerEpoch || session.value.uid != uid || session.value.household != household) return@addSnapshotListener
            if (error != null) fail("liveLocations", error)
            else if (snap != null) {
                // Nunca exibir localização em cache como se fosse confirmação recente do servidor.
                val locations = if (snap.metadata.isFromCache) emptyList() else snap.documents.mapNotNull { doc ->
                    val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                    val lon = doc.getDouble("longitude") ?: return@mapNotNull null
                    val captured = doc.getTimestamp("capturedAt")?.toDate()?.time ?: return@mapNotNull null
                    val expires = doc.getTimestamp("expiresAt")?.toDate()?.time ?: return@mapNotNull null
                    val end = doc.getTimestamp("sessionEndsAt")?.toDate()?.time ?: return@mapNotNull null
                    if (doc.id != doc.getString("ownerUid") || lat !in -90.0..90.0 || lon !in -180.0..180.0) null
                    else LiveLocation(doc.id, lat, lon, captured, expires, end)
                }
                mutableData.value = mutableData.value.copy(liveLocations = locations)
                publish()
            }
        }
        listeners += house.collection("baskets").addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (epoch != listenerEpoch || session.value.uid != uid || session.value.household != household) return@addSnapshotListener
            if (error != null) fail("baskets", error)
            else if (snap != null) { if (!snap.metadata.isFromCache) access.confirm("baskets"); mutableData.value = mutableData.value.copy(baskets = snap.documents.mapNotNull { d -> d.data?.let { d.id to Basket.from(it) } }.toMap()); sharedStatus = status(snap); publish() }
        }
    }
    private fun readRecord(doc: DocumentSnapshot): Record? { val m = doc.data ?: return null; return Record(doc.id, m.str("kind"), m["private"] == true, m - "kind" - "private") }

    suspend fun login(email: String, password: String) { require(email.isNotBlank() && password.isNotBlank()) { "Informe e-mail e senha." }; mutableSession.value = SessionState(); auth.signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun register(name: String, email: String, password: String) {
        require(name.isNotBlank()) { "Informe seu nome." }; require(password.length >= 8) { "Use uma senha com ao menos oito caracteres." }
        mutableSession.value = SessionState()
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user ?: error("Cadastro não confirmado.")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        db.collection("users").document(user.uid).set(mapOf("displayName" to name.trim(), "householdId" to "")).await()
        user.sendEmailVerification().await()
        mutableSession.value = mutableSession.value.copy(name = name.trim())
    }
    suspend fun reset(email: String) { require(email.isNotBlank()) { "Informe seu e-mail." }; auth.sendPasswordResetEmail(email.trim()).await() }
    suspend fun verifyEmail() { auth.currentUser?.sendEmailVerification()?.await() }
    suspend fun refreshVerification() { auth.currentUser?.reload()?.await(); auth.currentUser?.getIdToken(true)?.await(); mutableSession.value = mutableSession.value.copy(verified = auth.currentUser?.isEmailVerified == true) }
    fun logout() { closeListeners(); mutableSession.value = SessionState(); mutableData.value = DataState(); auth.signOut(); prefs.edit().putString("activeUid", "").apply() }
    fun useLocal(name: String) {
        require(name.isNotBlank()) { "Informe seu nome." }; closeListeners(); auth.signOut()
        mutableSession.value = SessionState("local", name.trim(), local = true, household = "local")
        prefs.edit().putString("activeUid", "local").putString("localName", name.trim()).apply()
        val root = JSONObject(prefs.getString("data", "{}") ?: "{}")
        val records = root.optJSONArray("records") ?: JSONArray()
        val baskets = root.optJSONObject("baskets") ?: JSONObject()
        mutableData.value = DataState((0 until records.length()).map { i -> val obj = records.getJSONObject(i); val map = obj.toMap(); Record(map.str("id"), map.str("kind"), map["private"] == true, map - "id" - "kind" - "private") }, baskets.keys().asSequence().associateWith { Basket.from(baskets.getJSONObject(it).toMap()) }, "Salvo neste aparelho • sem compartilhamento")
    }
    private fun persistLocal(next: DataState) {
        val root = JSONObject().put("records", JSONArray(next.records.map { JSONObject(it.toMap() + ("id" to it.id)) })).put("baskets", JSONObject(next.baskets.mapValues { JSONObject(it.value.toMap()) }))
        check(prefs.edit().putString("data", root.toString()).commit()) { "Não foi possível salvar no aparelho." }
        mutableData.value = next.copy(sync = "Salvo neste aparelho • sem compartilhamento")
    }
    private fun recordCollection(isPrivate: Boolean): CollectionReference {
        val user = session.value; require(user.uid.isNotEmpty()) { "Entre na sua conta." }
        return if (isPrivate) db.collection("users").document(user.uid).collection("records") else {
            require(user.household.isNotBlank()) { "Crie ou aceite um espaço do casal." }; db.collection("households").document(user.household).collection("records")
        }
    }
    suspend fun save(record: Record) {
        require(record.kind in setOf("task", "occurrence", "plan", "contribution", "moment", "transaction", "health", "quest", "catalogLink", "safetyCheckIn", "safetyAlert", "smartDevice", "careEvent"))
        if (record.kind == "catalogLink") {
            require(!record.isPrivate && record.fields.keys.all { it in setOf("skuId", "baseItemId", "status", "revision", "updatedBy", "updatedAt") }) { "O vínculo aceita apenas dados de confirmação." }
            require(record.id == record.text("skuId")) { "O vínculo precisa identificar o SKU." }
            saveCatalogLink(record.id, record.text("baseItemId"), record.text("status"), (record.fields["revision"] as? Number)?.toLong())
            return
        }
        if (session.value.local) { persistLocal(data.value.copy(records = data.value.records.filterNot { it.id == record.id } + record)); return }
        val previous=data.value.records.firstOrNull { it.id==record.id }
        if(previous != null && previous.isPrivate != record.isPrivate) {
            val batch=db.batch()
            batch.delete(recordCollection(previous.isPrivate).document(record.id))
            batch.set(recordCollection(record.isPrivate).document(record.id),record.toMap()+mapOf("updatedBy" to session.value.uid,"updatedAt" to FieldValue.serverTimestamp()))
            batch.commit().await()
            return
        }
        recordCollection(record.isPrivate).document(record.id).set(record.toMap() + mapOf("updatedBy" to session.value.uid, "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    /** Inventory is user-maintained planning data on a shared catalog item; imported receipt fields stay untouched. */
    suspend fun saveCatalogInventory(item: Record, stockMilli: Long?, storageRoom: String) {
        require(item.kind == "catalogItem" && !item.isPrivate) { "Só produtos da base compartilhada aceitam estoque." }
        require(stockMilli == null || stockMilli in 0..100_000_000L) { "Quantidade em casa inválida." }
        require(storageRoom.length <= 60) { "Cômodo inválido." }
        val fields = item.fields + mapOf("stockKnown" to (stockMilli != null), "stockMilli" to (stockMilli ?: 0L), "storageRoom" to storageRoom)
        val next = item.copy(fields = fields)
        if (session.value.local) {
            persistLocal(data.value.copy(records = data.value.records.filterNot { it.id == item.id } + next))
            return
        }
        recordCollection(false).document(item.id).update(mapOf(
            "stockKnown" to (stockMilli != null), "stockMilli" to (stockMilli ?: 0L), "storageRoom" to storageRoom,
            "updatedBy" to session.value.uid, "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }
    /** Stores a human-approved display label separately from the immutable fiscal name. */
    suspend fun saveCatalogDisplayName(sku: Record, displayName: String) {
        require(sku.kind == "catalogSku" && !sku.isPrivate) { "Somente itens importados da nota podem receber um nome de exibição." }
        val name = displayName.trim().replace(Regex("\\s+"), " ")
        require(name.length in 2..120) { "Use um nome entre 2 e 120 caracteres." }
        val next = sku.copy(fields = sku.fields + ("displayName" to name))
        if (session.value.local) {
            persistLocal(data.value.copy(records = data.value.records.filterNot { it.id == sku.id } + next))
            return
        }
        recordCollection(false).document(sku.id).update(mapOf("displayName" to name, "updatedBy" to session.value.uid, "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    /** Creates a planning product from a receipt SKU so a broad category never absorbs its history. */
    suspend fun createCatalogBaseFromSku(sku: Record, title: String): Record {
        require(sku.kind == "catalogSku" && !sku.isPrivate) { "Escolha um item importado válido." }
        val cleanTitle = title.trim().replace(Regex("\\s+"), " ")
        require(cleanTitle.length in 2..120 && !isGenericCatalogBase(cleanTitle)) { "Dê um nome específico ao produto." }
        val record = Record("base-${UUID.randomUUID()}", "catalogItem", fields = mapOf(
            "title" to cleanTitle,
            "market" to sku.text("market"),
            "category" to sku.text("category").ifBlank { "Temperos e condimentos" },
            "unit" to sku.text("unit"),
            "storageRoom" to "Cozinha",
            "createdFromSku" to sku.id
        ))
        if (session.value.local) {
            persistLocal(data.value.copy(records = data.value.records + record))
        } else {
            recordCollection(false).document(record.id).set(record.toMap() + mapOf(
                "updatedBy" to session.value.uid, "updatedAt" to FieldValue.serverTimestamp()
            )).await()
        }
        return record
    }
    /** A chapter is an explicit user record, never inferred from a contribution or imported decision. */
    suspend fun savePlanStep(plan: Record, stepId: String, title: String? = null, completed: Boolean? = null, expectedRevision: Long): Record {
        require(plan.kind == "plan") { "Somente planos criados no app aceitam etapas." }
        require(stepId.isNotBlank() && stepId.length <= 100 && '/' !in stepId) { "Etapa inválida." }
        require(expectedRevision >= 0) { "Revisão de etapas inválida." }
        require(title != null || completed != null) { "Informe a mudança da etapa." }
        if (title != null) require(title.trim().isNotEmpty() && title.trim().length <= 200) { "Dê um título de até 200 caracteres à etapa." }
        val user = session.value; require(user.uid.isNotBlank()) { "Entre na sua conta." }
        fun next(old: Record?, timestamp: Any): Record {
            require(old != null && old.kind == "plan" && old.isPrivate == plan.isPrivate) { "Plano não encontrado neste espaço." }
            val revision = old.number("stageRevision")
            require(revision == expectedRevision && revision < Long.MAX_VALUE) { "As etapas mudaram. Confira novamente antes de salvar." }
            val steps = (old.fields["steps"] as? List<*>)?.map { raw ->
                val map = raw as? Map<*, *> ?: error("Etapa existente inválida.")
                require(map["id"] is String && map["title"] is String && map["done"] is Boolean) { "Etapa existente inválida." }
                map.entries.associate { (key, value) -> (key as? String ?: error("Campo de etapa inválido.")) to value }
            } ?: emptyList()
            require(steps.map { it["id"] }.distinct().size == steps.size) { "Há etapas duplicadas para revisar." }
            val index = steps.indexOfFirst { it["id"] == stepId }
            require(index >= 0 || title != null) { "Esta etapa não existe mais." }
            require(index >= 0 || steps.size < 100) { "Use até 100 etapas por plano." }
            val changed = if (index >= 0) steps[index] + (title?.let { mapOf("title" to it.trim()) } ?: emptyMap()) + (completed?.let { mapOf("done" to it) } ?: emptyMap())
                else mapOf("id" to stepId, "title" to title!!.trim(), "done" to (completed ?: false))
            val updated = if (index >= 0) steps.mapIndexed { i, step -> if (i == index) changed else step } else steps + listOf(changed)
            return old.copy(fields = old.fields + mapOf("steps" to updated, "stageRevision" to revision + 1, "updatedBy" to user.uid, "updatedAt" to timestamp))
        }
        if (user.local) return planLocalMutex.withLock {
            val result = next(data.value.records.firstOrNull { it.id == plan.id }, java.time.Instant.now().toString())
            persistLocal(data.value.copy(records = data.value.records.filterNot { it.id == plan.id } + result)); result
        }
        require(auth.currentUser?.uid == user.uid) { "Entre novamente para alterar as etapas." }
        val ref = recordCollection(plan.isPrivate).document(plan.id)
        return db.runTransaction { tx -> val result = next(readRecord(tx.get(ref)), FieldValue.serverTimestamp()); tx.set(ref, result.toMap()); result }.await()
    }
    /** Editing goal fields reads the latest record so concurrent chapter changes survive. */
    suspend fun savePlanDetails(record: Record): Record {
        require(record.kind == "plan") { "Referências importadas não são metas editáveis." }
        val changes = record.fields.filterKeys { it in setOf("title", "note", "target", "horizon", "date") }
        require((changes["title"] as? String)?.trim()?.isNotEmpty() == true) { "Informe o título do plano." }
        if (changes.containsKey("target")) require(changes["target"] == null || changes["target"] is Number && (changes["target"] as Number).toLong() >= 0) { "Meta inválida." }
        val user = session.value; require(user.uid.isNotBlank()) { "Entre na sua conta." }
        fun next(old: Record?, timestamp: Any): Record {
            require(old != null && old.kind == "plan" && old.isPrivate == record.isPrivate) { "Plano não encontrado neste espaço." }
            return old.copy(fields = old.fields + changes + mapOf("updatedBy" to user.uid, "updatedAt" to timestamp))
        }
        if (user.local) return planLocalMutex.withLock {
            val result = next(data.value.records.firstOrNull { it.id == record.id }, java.time.Instant.now().toString())
            persistLocal(data.value.copy(records = data.value.records.filterNot { it.id == record.id } + result)); result
        }
        require(auth.currentUser?.uid == user.uid) { "Entre novamente para editar o plano." }
        val ref = recordCollection(record.isPrivate).document(record.id)
        return db.runTransaction { tx -> val result = next(readRecord(tx.get(ref)), FieldValue.serverTimestamp()); tx.set(ref, result.toMap()); result }.await()
    }
    /** Explicit human decision only; imported SKU/base documents and price history remain unchanged. */
    suspend fun saveCatalogLink(skuId: String, baseItemId: String, status: String, expectedRevision: Long? = null): Record {
        require(skuId.isNotBlank() && '/' !in skuId && skuId != "." && skuId != "..") { "SKU inválido." }
        require(status in setOf("confirmed", "separate")) { "Escolha confirmar ou manter separado." }
        require((status == "confirmed" && baseItemId.isNotBlank() && '/' !in baseItemId && baseItemId != "." && baseItemId != "..") || (status == "separate" && baseItemId.isEmpty())) { "Selecione um item-base para confirmar ou deixe vazio para manter separado." }
        val user = session.value
        require(user.uid.isNotBlank()) { "Entre na sua conta." }
        fun next(old: Record?, timestamp: Any): Record {
            require(old == null || old.kind == "catalogLink") { "Registro de vínculo inválido." }
            val revision = old?.number("revision") ?: 0L
            require(expectedRevision == null || revision == expectedRevision) { "O vínculo mudou. Revise novamente." }
            return Record(skuId, "catalogLink", fields = mapOf("skuId" to skuId, "baseItemId" to baseItemId, "status" to status, "revision" to revision + 1, "updatedBy" to user.uid, "updatedAt" to timestamp))
        }
        if (user.local) {
            require(data.value.records.any { it.id == skuId && it.kind == "catalogSku" && !it.isPrivate }) { "SKU não encontrado no catálogo deste espaço." }
            if (status == "confirmed") require(data.value.records.any { it.id == baseItemId && it.kind == "catalogItem" && !it.isPrivate }) { "Item-base não encontrado no catálogo deste espaço." }
            val result = next(data.value.records.firstOrNull { it.id == skuId && it.kind == "catalogLink" }, java.time.Instant.now().toString())
            persistLocal(data.value.copy(records = data.value.records.filterNot { it.id == skuId && it.kind == "catalogLink" } + result))
            return result
        }
        require(user.household.isNotBlank()) { "Crie ou aceite um espaço do casal." }
        require(auth.currentUser?.uid == user.uid) { "Entre novamente para confirmar o vínculo." }
        val house = db.collection("households").document(user.household)
        val ref = house.collection("catalogLinks").document(skuId)
        return db.runTransaction { tx ->
            val sku = tx.get(house.collection("records").document(skuId))
            require(sku.getString("kind") == "catalogSku" && sku.getBoolean("private") == false) { "SKU não encontrado no catálogo deste espaço." }
            if (status == "confirmed") {
                val base = tx.get(house.collection("records").document(baseItemId))
                require(base.getString("kind") == "catalogItem" && base.getBoolean("private") == false) { "Item-base não encontrado no catálogo deste espaço." }
            }
            val old = tx.get(ref).let(::readRecord)
            val result = next(old, FieldValue.serverTimestamp())
            tx.set(ref, result.toMap())
            result
        }.await()
    }
    /** Idempotently links one plan step to a shared/local shopping basket. */
    suspend fun linkPlanStepToBasket(planId: String, stepId: String, title: String, market: String) {
        require(planId.isNotBlank() && stepId.isNotBlank()) { "Origem do plano inválida." }
        require(title.trim().isNotEmpty() && title.trim().length <= 200) { "Etapa inválida." }
        require(market in listOf("Mercado", "Açougue")) { "Escolha Mercado ou Açougue." }
        val itemId = "plan-${planId}-${stepId}"
        fun linked(old: Basket): Basket {
            if (old.entries.any { it.sourcePlanId == planId && it.sourceStepId == stepId }) return old
            require(old.entries.size < 200) { "Use até 200 itens por lista." }
            val entry = Entry(
                id = itemId,
                title = title.trim(),
                unit = "un",
                plannedMilli = 1000,
                actualMilli = 1000,
                estimated = 0,
                actual = null,
                bought = false,
                estimateKnown = false,
                unitReviewed = false,
                quantityReviewed = false,
                storageRoom = "Casa",
                recipient = "Casa",
                group = "Outro",
                priority = "Normal",
                sourcePlanId = planId,
                sourceStepId = stepId
            )
            return old.copy(entries = old.entries + entry)
        }
        val user = session.value
        require(user.uid.isNotBlank()) { "Entre na sua conta." }
        val otherMarket = if (market == "Mercado") "Açougue" else "Mercado"
        fun hasLink(basket: Basket) = basket.entries.any { it.sourcePlanId == planId && it.sourceStepId == stepId }
        if (user.local) {
            val old = data.value.baskets[market] ?: Basket(market)
            if (hasLink(old)) return
            require(!hasLink(data.value.baskets[otherMarket] ?: Basket(otherMarket))) {
                "Esta etapa já está na outra lista de compras."
            }
            val next = linked(old)
            persistLocal(data.value.copy(baskets = data.value.baskets + (market to next.copy(revision = old.revision + 1))))
            return
        }
        require(user.household.isNotBlank()) { "Conecte o espaço do casal antes de usar uma lista compartilhada." }
        val baskets = db.collection("households").document(user.household).collection("baskets")
        val ref = baskets.document(market)
        val otherRef = baskets.document(otherMarket)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val otherSnap = tx.get(otherRef)
            val old = snap.data?.let(Basket::from) ?: Basket(market)
            if (!hasLink(old)) {
                val other = otherSnap.data?.let(Basket::from) ?: Basket(otherMarket)
                require(!hasLink(other)) { "Esta etapa já está na outra lista de compras." }
                val next = linked(old)
                tx.set(ref, next.copy(revision = old.revision + 1).toMap())
            }
        }.await()
    }

    /** Creates at most one routine task for a plan step, preserving the origin. */
    suspend fun createPlanStepTask(planId: String, stepId: String, title: String, date: String, owner: String, category: String, time: String, isPrivate: Boolean): Record {
        require(planId.isNotBlank() && stepId.isNotBlank()) { "Origem do plano inválida." }
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 200) { "Etapa inválida." }
        java.time.LocalDate.parse(date)
        require(owner in setOf("Tiago", "Rhayra", "Ambos")) { "Escolha o responsável." }
        require(category in setOf("Pessoal","Casa","Lucca","Rotina")) { "Escolha a área da tarefa." }
        if (time.isNotBlank()) java.time.LocalTime.parse(time)
        val id = "plan-task-${planId}-${stepId}"
        val task = Record(id, "task", isPrivate, mapOf(
            "title" to cleanTitle,
            "note" to "Criada a partir de uma etapa de plano.",
            "date" to date,
            "owner" to owner,
            "category" to category,
            "repeat" to "Única",
            "time" to time,
            "skipReminder" to time.isBlank(),
            "needsReview" to false,
            "needsTimeReview" to false,
            "sourcePlanId" to planId,
            "sourceStepId" to stepId
        ))
        val user = session.value
        require(user.uid.isNotBlank()) { "Entre na sua conta." }
        if (user.local) return planLocalMutex.withLock {
            require(data.value.records.none { it.kind == "task" && it.text("sourcePlanId") == planId && it.text("sourceStepId") == stepId }) { "Esta etapa já está na Rotina." }
            persistLocal(data.value.copy(records = data.value.records + task))
            task
        }
        require(auth.currentUser?.uid == user.uid) { "Entre novamente para criar a tarefa." }
        val ref = recordCollection(isPrivate).document(id)
        return db.runTransaction { tx ->
            require(!tx.get(ref).exists()) { "Esta etapa já está na Rotina." }
            tx.set(ref, task.toMap() + mapOf("updatedBy" to user.uid, "updatedAt" to FieldValue.serverTimestamp()))
            task
        }.await()
    }

    suspend fun remove(record: Record) {
        require(record.kind in setOf("task", "occurrence", "plan", "moment", "transaction", "health", "careEvent", "safetyCheckIn")) { "Este histórico é preservado." }
        if (record.kind == "safetyCheckIn") require(record.text("ownerUid") == session.value.uid) { "Só você pode remover sua localização manual." }
        if (session.value.local) { persistLocal(data.value.copy(records = data.value.records.filterNot { it.id == record.id })); return }
        recordCollection(record.isPrivate).document(record.id).delete().await()
    }
    suspend fun questResponse(record: Record, phase: String) {
        require(phase in setOf("accept", "complete", "decline", "cancel"))
        require(!session.value.local) { "Missões em dupla exigem duas contas autenticadas." }
        val uid = session.value.uid
        val ref = recordCollection(false).document(record.id)
        db.runTransaction { tx ->
            val old = tx.get(ref).data ?: error("Convite não encontrado.")
            require(old["cancelled"] != true) { "Este convite foi encerrado." }
            val accepted = old["accepted"] as? List<*> ?: emptyList<Any>()
            when (phase) {
                "accept" -> tx.update(ref, "accepted", FieldValue.arrayUnion(uid))
                "complete" -> { require(accepted.size == 2 && uid in accepted) { "Aguarde o aceite dos dois." }; tx.update(ref, "completed", FieldValue.arrayUnion(uid)) }
                "decline" -> tx.update(ref, "cancelled", true)
                "cancel" -> { require(old["creatorUid"] == uid) { "Somente quem propôs pode cancelar." }; tx.update(ref, "cancelled", true) }
            }
        }.await()
    }
    suspend fun createHouse() {
        val user = session.value; require(!user.local && user.household.isBlank()) { "Você já possui um espaço." }
        val id = UUID.randomUUID().toString()
        db.runBatch { b -> b.set(db.collection("households").document(id), mapOf("name" to "Nosso espaço", "creatorUid" to user.uid, "members" to listOf(user.uid), "inviteId" to "", "createdAt" to FieldValue.serverTimestamp())); b.set(db.collection("users").document(user.uid), mapOf("displayName" to user.name, "householdId" to id)) }.await()
    }
    suspend fun invite(email: String): String {
        val user = session.value; require(!user.local && user.household.isNotBlank())
        require(email.contains('@')) { "Informe o e-mail do parceiro." }
        val id = UUID.randomUUID().toString().replace("-", "")
        val house = db.collection("households").document(user.household)
        db.runTransaction { tx ->
            val h = tx.get(house); require((h.get("members") as? List<*>)?.size == 1) { "O espaço já tem dois participantes." }
            require(h.getString("creatorUid") == user.uid) { "Só o criador pode convidar." }
            tx.set(db.collection("invites").document(id), mapOf("householdId" to user.household, "ownerUid" to user.uid, "email" to email.trim().lowercase(), "expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + 86400000)), "usedBy" to ""))
            tx.update(house, "inviteId", id)
        }.await(); return id
    }
    suspend fun accept(code: String) {
        refreshVerification(); val user = session.value
        require(!user.local && user.household.isBlank()) { "Saia do modo local ou use uma conta sem espaço." }
        require(user.verified) { "Confirme o e-mail antes de aceitar o convite." }
        val ref = db.collection("invites").document(code.trim())
        db.runTransaction { tx ->
            val snap = tx.get(ref); require(snap.exists()) { "Convite não encontrado." }
            require(snap.getString("email") == user.email.lowercase() && snap.getString("usedBy").orEmpty().isBlank()) { "Convite indisponível para esta conta." }
            require((snap.getTimestamp("expiresAt")?.toDate()?.time ?: 0) > System.currentTimeMillis()) { "Convite expirado." }
            val hid = snap.getString("householdId") ?: error("Espaço inválido.")
            tx.update(ref, "usedBy", user.uid)
            tx.update(db.collection("households").document(hid), "members", FieldValue.arrayUnion(user.uid))
            tx.set(db.collection("users").document(user.uid), mapOf("displayName" to user.name, "householdId" to hid))
        }.await()
    }
    suspend fun leaveHouse() {
        val user = session.value; require(!user.local && user.household.isNotBlank())
        db.runBatch { b -> b.update(db.collection("households").document(user.household), "members", FieldValue.arrayRemove(user.uid)); b.update(db.collection("users").document(user.uid), "householdId", "") }.await()
    }
    suspend fun editBasket(market: String, expectedRevision: Long?, transform: (Basket) -> Basket) {
        require(market in listOf("Mercado", "Açougue"))
        if (session.value.local) {
            val old = data.value.baskets[market] ?: Basket(market)
            require(expectedRevision == null || old.revision == expectedRevision) { "A lista mudou. Abra o item novamente." }
            persistLocal(data.value.copy(baskets = data.value.baskets + (market to transform(old).copy(revision = old.revision + 1)))); return
        }
        require(session.value.household.isNotBlank()) { "Crie o espaço primeiro." }
        val ref = db.collection("households").document(session.value.household).collection("baskets").document(market)
        db.runTransaction { tx ->
            val snap = tx.get(ref); val old = snap.data?.let(Basket::from) ?: Basket(market)
            require(expectedRevision == null || old.revision == expectedRevision) { "A lista mudou em outro aparelho. Abra novamente." }
            val next = transform(old).copy(revision = old.revision + 1)
            require(next.entries.size <= 200) { "Limite de 200 itens por sessão." }
            tx.set(ref, next.toMap())
        }.await()
    }
    suspend fun closeBasket(market: String, generation: String, expectedRevision: Long) {
        if (session.value.local) {
            if (data.value.records.any { it.kind == "receipt" && it.id == generation }) return
            val old = data.value.baskets[market] ?: error("Lista não encontrada.")
            require(old.generation == generation && old.revision == expectedRevision) { "A lista mudou. Revise novamente." }; old.validateClose()
            val receipt = Record(generation, "receipt", fields = old.toMap() + mapOf("total" to old.real(), "date" to java.time.LocalDate.now().toString()))
            val next = Basket(market, budget = old.budget, revision = old.revision + 1, entries = old.entries.map { if (it.bought) it.copy(estimated = it.actual!!, bought = false, actual = null, estimateKnown = true, unitReviewed = true, quantityReviewed = true) else it })
            persistLocal(data.value.copy(records = data.value.records + receipt, baskets = data.value.baskets + (market to next))); return
        }
        val house = db.collection("households").document(session.value.household)
        val ref = house.collection("baskets").document(market); val receiptRef = house.collection("records").document(generation)
        db.runTransaction { tx ->
            if (tx.get(receiptRef).exists()) return@runTransaction
            val old = tx.get(ref).data?.let(Basket::from) ?: error("Lista não encontrada.")
            require(old.generation == generation && old.revision == expectedRevision) { "A lista mudou. Revise antes de concluir." }; old.validateClose()
            tx.set(receiptRef, old.toMap() + mapOf("kind" to "receipt", "private" to false, "total" to old.real(), "date" to java.time.LocalDate.now().toString(), "updatedBy" to session.value.uid, "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(ref, Basket(market, budget = old.budget, revision = old.revision + 1, entries = old.entries.map { if (it.bought) it.copy(estimated = it.actual!!, bought = false, actual = null, estimateKnown = true, unitReviewed = true, quantityReviewed = true) else it }).toMap())
        }.await()
    }
    fun export(): String = JSONObject().put("schemaVersion", 1).put("mode", if (session.value.local) "local" else "firebase-cache").put("exportedAt", java.time.Instant.now().toString()).put("records", JSONArray(data.value.records.map { JSONObject(it.toMap() + ("id" to it.id)) })).put("baskets", JSONObject(data.value.baskets.mapValues { JSONObject(it.value.toMap()) })).toString(2)
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> decode(get(key)) }
private fun decode(value: Any?): Any? = when (value) { JSONObject.NULL -> null; is JSONObject -> value.toMap(); is JSONArray -> (0 until value.length()).map { decode(value.get(it)) }; else -> value }

