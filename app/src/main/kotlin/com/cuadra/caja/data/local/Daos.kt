package com.cuadra.caja.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    /** Todo el catálogo activo: la caja busca en memoria (sin tildes ni mayúsculas, ver `ProductSearch`). */
    @Query("SELECT * FROM products WHERE active = 1")
    fun active(): Flow<List<ProductEntity>>

    @Query(
        """SELECT * FROM products WHERE active = 1 AND (name LIKE '%' || :q || '%' OR variant LIKE '%' || :q || '%' OR barcode = :q OR shortCode = :q)
           ORDER BY name COLLATE NOCASE LIMIT 100""",
    )
    fun search(q: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name COLLATE NOCASE LIMIT 100")
    fun all(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE active = 1 AND barcode IN (:forms) LIMIT 1")
    suspend fun byBarcodeForms(forms: List<String>): ProductEntity?

    /** Otro producto activo con este código (o su forma UPC/EAN equivalente): el servidor no admite dos. */
    @Query("SELECT * FROM products WHERE active = 1 AND barcode IN (:forms) AND id <> :exceptId LIMIT 1")
    suspend fun otherWithBarcode(forms: List<String>, exceptId: String): ProductEntity?

    @Query("SELECT * FROM product_categories WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM product_categories WHERE id = :id")
    suspend fun category(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products WHERE active = 1")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE status = 'PARKED' ORDER BY updatedAt DESC")
    fun parked(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE status IN ('COMPLETED', 'CANCELLED') ORDER BY COALESCE(completedAt, createdAt) DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun get(id: String): SaleEntity?

    /** Productos más vendidos desde `since` (ventas cobradas en este teléfono): en cuántas ventas salió y, a igual número, la cantidad. */
    @Query(
        """SELECT i.productId AS productId, COUNT(DISTINCT i.saleId) AS sales, SUM(i.quantityMilli) AS quantityMilli
           FROM sale_items i JOIN sales s ON s.id = i.saleId
           WHERE s.status = 'COMPLETED' AND s.completedAt >= :since AND i.productId IS NOT NULL
           GROUP BY i.productId ORDER BY sales DESC, quantityMilli DESC LIMIT :limit""",
    )
    fun bestSellers(since: Long, limit: Int): Flow<List<BestSellerRow>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY position")
    suspend fun items(saleId: String): List<SaleItemEntity>

    @Query("SELECT * FROM sale_payments WHERE saleId = :saleId ORDER BY position")
    suspend fun payments(saleId: String): List<SalePaymentEntity>

    @Query("SELECT * FROM sales WHERE status = 'OPEN'")
    suspend fun openSales(): List<SaleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<SalePaymentEntity>)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteItems(saleId: String)

    @Query("DELETE FROM sale_payments WHERE saleId = :saleId")
    suspend fun deletePayments(saleId: String)

    /** Una venta que el servidor nunca aceptó (`rev = 0`) y alguien descartó en «Requiere atención»: se quita del teléfono. */
    @Query("DELETE FROM sales WHERE id = :id AND rev = 0")
    suspend fun deleteUnconfirmed(id: String): Int

    /** Total y cantidad de ventas cobradas en un rango (jornada) según lo que este teléfono conoce. */
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(totalMinor), 0) AS total FROM sales WHERE status = 'COMPLETED' AND completedAt >= :from AND completedAt < :to")
    fun dayTotals(from: Long, to: Long): Flow<DayTotals>

    @Query(
        """SELECT p.method AS method, COALESCE(SUM(p.amountMinor), 0) AS total FROM sale_payments p JOIN sales s ON s.id = p.saleId
           WHERE s.status = 'COMPLETED' AND s.completedAt >= :from AND s.completedAt < :to GROUP BY p.method""",
    )
    fun dayByMethod(from: Long, to: Long): Flow<List<MethodTotal>>

    // ---------- devoluciones ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReturn(r: SaleReturnEntity)

    @Query("SELECT * FROM sale_returns WHERE saleId = :saleId ORDER BY occurredAt, id")
    suspend fun returnsFor(saleId: String): List<SaleReturnEntity>

    /** Las devoluciones confirmadas de una venta se reemplazan con las que trae el servidor; las pendientes (`rev = 0`) se quedan hasta que lleguen. */
    @Query("DELETE FROM sale_returns WHERE saleId = :saleId AND rev > 0")
    suspend fun deleteConfirmedReturns(saleId: String)

    @Query("DELETE FROM sale_returns WHERE id = :id AND rev = 0")
    suspend fun deleteUnconfirmedReturn(id: String): Int

    @Query("UPDATE sale_items SET returnedMilli = :milli WHERE saleId = :saleId AND id = :itemId")
    suspend fun setReturned(saleId: String, itemId: String, milli: Long)

    @Query("UPDATE sales SET returnedMinor = :minor WHERE id = :saleId")
    suspend fun setSaleReturned(saleId: String, minor: Long)

    /** Lo devuelto en un rango (jornada) según lo que este teléfono conoce: resta en el Resumen del día en que se devolvió. */
    @Query("SELECT COALESCE(SUM(totalMinor), 0) FROM sale_returns WHERE occurredAt >= :from AND occurredAt < :to")
    fun returnedBetween(from: Long, to: Long): Flow<Long>

    /** La última venta cobrada por esa persona en este teléfono (para «Anular mi última venta»). */
    @Query("SELECT * FROM sales WHERE status = 'COMPLETED' AND COALESCE(completedByMemberId, createdByMemberId) = :memberId ORDER BY completedAt DESC, createdAt DESC LIMIT 1")
    suspend fun lastCompletedBy(memberId: String): SaleEntity?
}

data class DayTotals(val count: Int, val total: Long)
data class MethodTotal(val method: String, val total: Long)

@Dao
abstract class OutboxDao {
    @Insert
    abstract suspend fun insertRow(op: OutboxEntity): Long

    /** Encola una operación sellada con quien la hace y el negocio actual (`OutboxStamp`). Siempre por aquí, nunca `insertRow`. */
    open suspend fun insert(op: OutboxEntity): Long = insertRow(OutboxStamp.apply(op))

    /**
     * Lo que toca enviar AL negocio actual. Las filas sin negocio son de antes de separar por negocio: son del único que había en el teléfono.
     * Sin persona activa (`hasMember` falso) solo salen las que dicen quién las hizo: una fila vieja sin autor espera a que alguien entre.
     */
    @Query("SELECT * FROM outbox WHERE state = 'PENDING' AND nextAttemptAt <= :now AND (businessId IS NULL OR businessId = :businessId) AND (:hasMember OR memberId IS NOT NULL) ORDER BY seq LIMIT :limit")
    abstract suspend fun due(limit: Int, now: Long, businessId: String?, hasMember: Boolean = true): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE seq IN (:seqs)")
    abstract suspend fun delete(seqs: List<Long>)

    @Query("UPDATE outbox SET state = 'FAILED', lastCode = :code, lastDetail = :detail, attempts = attempts + 1 WHERE seq = :seq")
    abstract suspend fun markFailed(seq: Long, code: String, detail: String?)

    /** Se aplicó, pero hay algo que revisar (la venta se guardó aparte, o ya estaba descartada): deja de estar pendiente y aparece en «Requiere atención». */
    @Query("UPDATE outbox SET state = 'REVIEW', lastCode = :code, lastDetail = :detail WHERE seq = :seq")
    abstract suspend fun markReview(seq: Long, code: String, detail: String?)

    @Query("UPDATE outbox SET attempts = attempts + 1, nextAttemptAt = :next, lastCode = :code WHERE seq IN (:seqs)")
    abstract suspend fun retryLater(seqs: List<Long>, next: Long, code: String?)

    @Query("SELECT COUNT(*) FROM outbox WHERE state = 'PENDING'")
    abstract fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE state = 'PENDING'")
    abstract suspend fun pendingCountNow(): Int

    /** Lo que requiere atención: rechazadas y aplicadas con algo que revisar. */
    @Query("SELECT COUNT(*) FROM outbox WHERE state IN ('FAILED', 'REVIEW')")
    abstract fun failedCount(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE state IN ('FAILED', 'REVIEW') ORDER BY seq")
    abstract fun failed(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE seq = :seq")
    abstract suspend fun get(seq: Long): OutboxEntity?

    /**
     * Operaciones SIN ENVIAR sobre un registro: mientras haya una, gana lo del teléfono y la bajada no lo pisa. Solo cuentan las pendientes: una
     * rechazada (FAILED) o ya aplicada (REVIEW) no debe congelar el registro, así el teléfono vuelve a aceptar la versión del servidor.
     */
    @Query("SELECT COUNT(*) FROM outbox WHERE entityId = :entityId AND state = 'PENDING'")
    abstract suspend fun countFor(entityId: String): Int

    /** Cuántas operaciones (pendientes o para revisar) hay de un negocio que NO es `businessId`: impiden cambiar de negocio. */
    @Query("SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING', 'FAILED') AND (businessId IS NULL OR businessId <> :businessId)")
    abstract suspend fun unsentOutside(businessId: String): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING', 'FAILED')")
    abstract suspend fun unsentCount(): Int

    /** «Reintentar»: vuelve a la cola con un id de operación NUEVO (el servidor recuerda el rechazo del viejo). Seguro: una rechazada nunca se aplicó. */
    @Query("UPDATE outbox SET state = 'PENDING', opId = :newOpId, attempts = 0, nextAttemptAt = 0, lastCode = NULL, lastDetail = NULL WHERE seq = :seq AND state = 'FAILED'")
    abstract suspend fun requeue(seq: Long, newOpId: String): Int

    @Insert
    abstract suspend fun insertDiscarded(line: DiscardedOpEntity): Long

    @Query("SELECT * FROM outbox_discarded ORDER BY discardedAt DESC LIMIT 200")
    abstract fun discarded(): Flow<List<DiscardedOpEntity>>

    /** «Descartar»: sale de la cola y queda una línea local de quién, cuándo y qué era. */
    @androidx.room.Transaction
    open suspend fun discard(seq: Long, at: Long, byId: String?, byName: String?): Boolean {
        val op = get(seq) ?: return false
        insertDiscarded(DiscardedOpEntity(opId = op.opId, kind = op.kind, entityId = op.entityId, payload = op.payload, createdAt = op.createdAt, memberId = op.memberId,
            businessId = op.businessId, code = op.lastCode, discardedAt = at, discardedById = byId, discardedByName = byName))
        delete(listOf(seq))
        return true
    }
}

@Dao
interface DirectoryDao {
    @Query("SELECT * FROM members WHERE status = 'ACTIVE' ORDER BY (role = 'OWNER') DESC, (role = 'ADMIN') DESC, displayName COLLATE NOCASE")
    fun activeMembers(): Flow<List<MemberEntity>>

    /** Todas las personas (también las deshabilitadas): la lista de gestión del equipo. */
    @Query("SELECT * FROM members ORDER BY (role = 'OWNER') DESC, (role = 'ADMIN') DESC, displayName COLLATE NOCASE")
    fun allMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun member(id: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<MemberEntity>)

    @Query("SELECT * FROM business LIMIT 1")
    fun business(): Flow<BusinessEntity?>

    @Query("SELECT * FROM business LIMIT 1")
    suspend fun businessNow(): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusiness(business: BusinessEntity)

    @Query("UPDATE business SET accessCode = :code WHERE id = :id")
    suspend fun setAccessCode(id: String, code: String)

    @Query("SELECT * FROM cash_registers WHERE active = 1 ORDER BY name LIMIT 1")
    suspend fun defaultRegister(): CashRegisterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegisters(registers: List<CashRegisterEntity>)

    @Query("SELECT cursor FROM sync_state WHERE id = 1")
    suspend fun cursor(): Long?

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun syncState(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCursor(state: SyncStateEntity)

    @Query("DELETE FROM members") suspend fun clearMembers()
}

/** Un fiado con el nombre de su cliente (si lo tiene) listo para mostrar en la lista. */
data class CreditItem(@androidx.room.Embedded val credit: CreditEntity, val customerName: String?)

data class CreditTotals(val openCount: Int, val openTotal: Long, val overdueCount: Int, val overdueTotal: Long)

@Dao
interface CustomerDao {
    @Query(
        """SELECT * FROM customers WHERE archived = 0 AND (:q = '' OR name LIKE '%' || :q || '%' OR (:digits != '' AND phone LIKE '%' || :digits || '%'))
           ORDER BY balanceMinor DESC, name COLLATE NOCASE LIMIT 200""",
    )
    fun search(q: String, digits: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: String): Flow<CustomerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(customers: List<CustomerEntity>)

    /** Nombres ya usados en fiados anteriores, para no escribir "Doña Karla" de tres maneras distintas. */
    @Query("SELECT debtorLabel FROM credits WHERE debtorLabel LIKE '%' || :q || '%' GROUP BY debtorLabel ORDER BY MAX(createdAt) DESC LIMIT 5")
    suspend fun labelSuggestions(q: String): List<String>

    @Query(
        """UPDATE customers SET balanceMinor = COALESCE((SELECT SUM(balanceMinor) FROM credits WHERE customerId = customers.id), 0),
           oldestOpenAt = (SELECT MIN(createdAt) FROM credits WHERE customerId = customers.id AND balanceMinor > 0) WHERE id IN (:ids)""",
    )
    suspend fun recompute(ids: List<String>)
}

@Dao
interface CreditDao {
    @Query(
        """SELECT c.*, cu.name AS customerName FROM credits c LEFT JOIN customers cu ON cu.id = c.customerId
           WHERE c.status = :status AND c.createdAt <= :olderThan
             AND (:linked = 'ALL' OR (:linked = 'WITH' AND c.customerId IS NOT NULL) OR (:linked = 'WITHOUT' AND c.customerId IS NULL))
             AND (:q = '' OR c.debtorLabel LIKE '%' || :q || '%' OR cu.name LIKE '%' || :q || '%' OR (:digits != '' AND c.debtorPhone LIKE '%' || :digits || '%'))
           ORDER BY c.createdAt, c.id""",
    )
    fun list(status: String, linked: String, olderThan: Long, q: String, digits: String): Flow<List<CreditItem>>

    @Query("SELECT c.*, cu.name AS customerName FROM credits c LEFT JOIN customers cu ON cu.id = c.customerId WHERE c.customerId = :customerId ORDER BY c.createdAt")
    fun forCustomer(customerId: String): Flow<List<CreditItem>>

    @Query(
        """SELECT COUNT(CASE WHEN status = 'OPEN' AND balanceMinor > 0 THEN 1 END) AS openCount,
                  COALESCE(SUM(CASE WHEN status = 'OPEN' THEN balanceMinor END), 0) AS openTotal,
                  COUNT(CASE WHEN status = 'OPEN' AND balanceMinor > 0 AND createdAt <= :overdueBefore THEN 1 END) AS overdueCount,
                  COALESCE(SUM(CASE WHEN status = 'OPEN' AND createdAt <= :overdueBefore THEN balanceMinor END), 0) AS overdueTotal FROM credits""",
    )
    fun totals(overdueBefore: Long): Flow<CreditTotals>

    @Query("SELECT * FROM credits WHERE id = :id")
    suspend fun get(id: String): CreditEntity?

    /** El fiado que nació de una venta (una venta puede tener más de un pago a fiado; basta el primero para registrar el envío). */
    @Query("SELECT id FROM credits WHERE saleId = :saleId LIMIT 1")
    suspend fun idBySale(saleId: String): String?

    @Query("SELECT * FROM credits WHERE customerId = :customerId AND status = 'OPEN' AND balanceMinor > 0")
    suspend fun openForCustomer(customerId: String): List<CreditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(credit: CreditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(credits: List<CreditEntity>)

    @Query("UPDATE credits SET customerId = :customerId, debtorPhone = COALESCE(debtorPhone, :phone) WHERE id = :id")
    suspend fun link(id: String, customerId: String, phone: String?)

    @Query("UPDATE credit_payments SET customerId = :customerId WHERE creditId = :creditId")
    suspend fun linkPayments(creditId: String, customerId: String)

    @Query("UPDATE credits SET lastReminderAt = :at WHERE id = :id")
    suspend fun markReminder(id: String, at: Long)

    /**
     * saldo = monto − abonos vigentes (0 si está condonado o cancelado); pagado cuando llega a 0. Se recalcula siempre desde los abonos
     * que este teléfono conoce (incluidos los aún sin enviar), así el orden en que llegue la información no hace parpadear el saldo.
     */
    @Query(
        """UPDATE credits SET
             balanceMinor = CASE WHEN status IN ('WRITTEN_OFF', 'CANCELLED') THEN 0
                 ELSE MAX(0, amountMinor - COALESCE((SELECT SUM(amountMinor) FROM credit_payments p WHERE p.creditId = credits.id AND p.voided = 0), 0)) END,
             status = CASE WHEN status IN ('WRITTEN_OFF', 'CANCELLED') THEN status
                 WHEN MAX(0, amountMinor - COALESCE((SELECT SUM(amountMinor) FROM credit_payments p WHERE p.creditId = credits.id AND p.voided = 0), 0)) = 0 THEN 'PAID'
                 ELSE 'OPEN' END
           WHERE id IN (:ids)""",
    )
    suspend fun recompute(ids: List<String>)

    @Query("SELECT DISTINCT customerId FROM credits WHERE id IN (:ids) AND customerId IS NOT NULL")
    suspend fun customersOf(ids: List<String>): List<String>

    @Query("SELECT id FROM credits")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM credit_payments WHERE creditId = :creditId ORDER BY occurredAt")
    suspend fun paymentsOf(creditId: String): List<CreditPaymentEntity>

    @Query("SELECT * FROM credit_payments WHERE customerId = :customerId ORDER BY occurredAt")
    fun paymentsForCustomer(customerId: String): Flow<List<CreditPaymentEntity>>

    @Query("SELECT DISTINCT customerId FROM credits WHERE (id = :id OR saleId = :id) AND customerId IS NOT NULL")
    suspend fun customersOfCredit(id: String): List<String>

    @Query("DELETE FROM credits WHERE (id = :id OR saleId = :id) AND rev = 0")
    suspend fun deleteUnconfirmedCredits(id: String)

    @Query("SELECT DISTINCT creditId FROM credit_payments WHERE (id = :id OR groupId = :id) AND rev = 0")
    suspend fun unconfirmedPaymentCredits(id: String): List<String>

    @Query("DELETE FROM credit_payments WHERE (id = :id OR groupId = :id) AND rev = 0")
    suspend fun deleteUnconfirmedPayments(id: String)

    @Query("SELECT creditId FROM credit_payments WHERE id = :id AND rev = 0")
    suspend fun unconfirmedPaymentCredit(id: String): String?

    @Query("DELETE FROM credit_payments WHERE id = :id AND rev = 0")
    suspend fun deleteUnconfirmedPaymentRow(id: String)

    @Query("SELECT * FROM credit_payments WHERE id = :id OR groupId = :id")
    suspend fun paymentsByIdOrGroup(id: String): List<CreditPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<CreditPaymentEntity>)

    @Query("UPDATE credit_payments SET voided = 1, voidReason = :reason WHERE id IN (:ids)")
    suspend fun voidPayments(ids: List<String>, reason: String?)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM message_templates WHERE kind = :kind AND locale = :locale")
    suspend fun get(kind: String, locale: String): TemplateEntity?

    @Query("SELECT * FROM message_templates")
    fun all(): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: TemplateEntity)

    @Query("DELETE FROM message_templates WHERE kind = :kind AND locale = :locale")
    suspend fun delete(kind: String, locale: String)

    @Query("SELECT * FROM message_templates")
    suspend fun allNow(): List<TemplateEntity>
}

/** Todo lo que movió una caja en una ventana de tiempo. Una sola fila para que la pantalla se refresque sola al cambiar cualquiera de las tablas. */
data class ShiftBreakdownRow(
    val cashSales: Long, val cashSalesCount: Int, val transfer: Long, val card: Long, val other: Long, val creditNew: Long, val creditCash: Long,
    val deposits: Long, val withdrawals: Long, val expensesCash: Long, val cancelled: Int,
)

data class ExpenseTotals(val cashDrawer: Long, val other: Long, val count: Int)

@Dao
interface CashDao {
    @Query("DELETE FROM expenses WHERE id = :id AND rev = 0")
    suspend fun deleteUnconfirmedExpense(id: String)

    @Query("DELETE FROM cash_movements WHERE id = :id AND rev = 0")
    suspend fun deleteUnconfirmedMovement(id: String)

    // ---------- categorías ----------
    @Query("SELECT * FROM expense_categories WHERE active = 1 ORDER BY COALESCE(name, key) COLLATE NOCASE")
    fun categories(): Flow<List<ExpenseCategoryEntity>>

    /** También las archivadas: los gastos viejos siguen mostrando el nombre de su categoría. */
    @Query("SELECT * FROM expense_categories")
    fun allCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE key = :key LIMIT 1")
    suspend fun categoryByKey(key: String): ExpenseCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<ExpenseCategoryEntity>)

    // ---------- gastos y movimientos ----------
    @Query("SELECT * FROM expenses WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC")
    fun expenses(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM cash_movements WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC")
    fun movements(from: Long, to: Long): Flow<List<CashMovementEntity>>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN source = 'CASH_DRAWER' THEN amountMinor END), 0) AS cashDrawer,
                  COALESCE(SUM(CASE WHEN source <> 'CASH_DRAWER' THEN amountMinor END), 0) AS other, COUNT(*) AS count
           FROM expenses WHERE voided = 0 AND occurredAt >= :from AND occurredAt < :to""",
    )
    fun expenseTotals(from: Long, to: Long): Flow<ExpenseTotals>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: String): ExpenseEntity?

    @Query("SELECT * FROM cash_movements WHERE id = :id")
    suspend fun movement(id: String): CashMovementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(e: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(items: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovement(m: CashMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovements(items: List<CashMovementEntity>)

    // ---------- turnos ----------
    @Query("SELECT * FROM shifts WHERE cashRegisterId = :register AND status = 'OPEN' LIMIT 1")
    fun openShift(register: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE cashRegisterId = :register AND status = 'OPEN' LIMIT 1")
    suspend fun openShiftNow(register: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun shift(id: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE cashRegisterId = :register AND status = 'CLOSED' ORDER BY closedAt DESC LIMIT 1")
    suspend fun lastClosed(register: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE cashRegisterId = :register ORDER BY openedAt DESC LIMIT :limit")
    fun recentShifts(register: String, limit: Int): Flow<List<ShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShift(s: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShifts(items: List<ShiftEntity>)

    /** Otro teléfono ganó la apertura de esa caja: el turno abierto aquí (ya sin operaciones pendientes) se descarta y queda el del servidor. */
    @Query("DELETE FROM shifts WHERE cashRegisterId = :register AND status = 'OPEN' AND id <> :keep AND (SELECT COUNT(*) FROM outbox WHERE entityId = shifts.id) = 0")
    suspend fun dropOtherOpenShifts(register: String, keep: String)

    @Query(
        """SELECT
             (SELECT COALESCE(SUM(p.amountMinor), 0) FROM sale_payments p JOIN sales s ON s.id = p.saleId WHERE s.status = 'COMPLETED' AND s.cashRegisterId = :register AND p.method = 'CASH' AND s.completedAt >= :from AND s.completedAt < :to) AS cashSales,
             (SELECT COUNT(DISTINCT s.id) FROM sale_payments p JOIN sales s ON s.id = p.saleId WHERE s.status = 'COMPLETED' AND s.cashRegisterId = :register AND p.method = 'CASH' AND s.completedAt >= :from AND s.completedAt < :to) AS cashSalesCount,
             (SELECT COALESCE(SUM(p.amountMinor), 0) FROM sale_payments p JOIN sales s ON s.id = p.saleId WHERE s.status = 'COMPLETED' AND s.cashRegisterId = :register AND p.method = 'TRANSFER' AND s.completedAt >= :from AND s.completedAt < :to) AS transfer,
             (SELECT COALESCE(SUM(p.amountMinor), 0) FROM sale_payments p JOIN sales s ON s.id = p.saleId WHERE s.status = 'COMPLETED' AND s.cashRegisterId = :register AND p.method = 'CARD' AND s.completedAt >= :from AND s.completedAt < :to) AS card,
             (SELECT COALESCE(SUM(p.amountMinor), 0) FROM sale_payments p JOIN sales s ON s.id = p.saleId WHERE s.status = 'COMPLETED' AND s.cashRegisterId = :register AND p.method = 'OTHER' AND s.completedAt >= :from AND s.completedAt < :to) AS other,
             (SELECT COALESCE(SUM(p.amountMinor), 0) FROM sale_payments p JOIN sales s ON s.id = p.saleId WHERE s.status = 'COMPLETED' AND s.cashRegisterId = :register AND p.method = 'CREDIT' AND s.completedAt >= :from AND s.completedAt < :to) AS creditNew,
             (SELECT COALESCE(SUM(amountMinor), 0) FROM credit_payments WHERE cashRegisterId = :register AND method = 'CASH' AND voided = 0 AND occurredAt >= :from AND occurredAt < :to) AS creditCash,
             (SELECT COALESCE(SUM(amountMinor), 0) FROM cash_movements WHERE cashRegisterId = :register AND kind = 'DEPOSIT' AND voided = 0 AND occurredAt >= :from AND occurredAt < :to) AS deposits,
             (SELECT COALESCE(SUM(amountMinor), 0) FROM cash_movements WHERE cashRegisterId = :register AND kind = 'WITHDRAWAL' AND voided = 0 AND occurredAt >= :from AND occurredAt < :to) AS withdrawals,
             (SELECT COALESCE(SUM(amountMinor), 0) FROM expenses WHERE cashRegisterId = :register AND source = 'CASH_DRAWER' AND voided = 0 AND occurredAt >= :from AND occurredAt < :to) AS expensesCash,
             (SELECT COUNT(*) FROM sales WHERE status = 'CANCELLED' AND cashRegisterId = :register AND updatedAt >= :from AND updatedAt < :to) AS cancelled""",
    )
    fun breakdown(register: String, from: Long, to: Long): Flow<ShiftBreakdownRow>
}

/** Un producto con lo que hay AHORA en el teléfono: lo que dice el servidor, más lo que este teléfono movió y aún no se confirma, menos lo vendido sin confirmar. */
data class ProductStock(@androidx.room.Embedded val product: ProductEntity, val stockNowMilli: Long)

data class PurchaseRow(@androidx.room.Embedded val purchase: PurchaseEntity, val paidMinor: Long)

data class SupplierBalance(val supplierId: String, val balanceMinor: Long)

/** Lo vendido de un producto en un periodo y lo que costó, con el costo que tenía CADA venta al momento de venderse. */
data class ProductProfit(val quantityMilli: Long, val revenueMinor: Long, val costMinor: Long, val costedRevenueMinor: Long)

@Dao
interface InventoryDao {
    // La existencia mostrada sigue la regla del plan: pendientes locales incluidos. Solo cuenta lo vendido de productos que llevan control.
    @Query(
        """SELECT * FROM (
             SELECT p.*, p.stockMilli
                + COALESCE((SELECT SUM(m.quantityMilli) FROM stock_movements m WHERE m.productId = p.id AND m.rev = 0), 0)
                - CASE WHEN p.trackStock = 1 THEN COALESCE((SELECT SUM(i.quantityMilli) FROM sale_items i JOIN sales s ON s.id = i.saleId
                       WHERE i.productId = p.id AND s.status = 'COMPLETED' AND s.rev = 0), 0) ELSE 0 END AS stockNowMilli
               FROM products p WHERE p.active = CASE :filter WHEN 'INACTIVE' THEN 0 ELSE 1 END AND (:q = '' OR p.name LIKE '%' || :q || '%' OR p.variant LIKE '%' || :q || '%' OR p.barcode = :q OR p.shortCode = :q)
           ) WHERE
             CASE :filter
               WHEN 'TRACKED' THEN trackStock = 1
               WHEN 'REVIEW' THEN trackStock = 1 AND (stockNowMilli < 0 OR (minStockMilli IS NOT NULL AND stockNowMilli <= minStockMilli))
               WHEN 'UNTRACKED' THEN trackStock = 0
               ELSE 1 END
           ORDER BY name COLLATE NOCASE LIMIT 300""",
    )
    fun stock(q: String, filter: String): Flow<List<ProductStock>>

    @Query(
        """SELECT p.*, p.stockMilli
              + COALESCE((SELECT SUM(m.quantityMilli) FROM stock_movements m WHERE m.productId = p.id AND m.rev = 0), 0)
              - CASE WHEN p.trackStock = 1 THEN COALESCE((SELECT SUM(i.quantityMilli) FROM sale_items i JOIN sales s ON s.id = i.saleId
                     WHERE i.productId = p.id AND s.status = 'COMPLETED' AND s.rev = 0), 0) ELSE 0 END AS stockNowMilli
             FROM products p WHERE p.id = :id""",
    )
    fun stockOf(id: String): Flow<ProductStock?>

    @Query(
        """SELECT p.*, p.stockMilli
              + COALESCE((SELECT SUM(m.quantityMilli) FROM stock_movements m WHERE m.productId = p.id AND m.rev = 0), 0)
              - CASE WHEN p.trackStock = 1 THEN COALESCE((SELECT SUM(i.quantityMilli) FROM sale_items i JOIN sales s ON s.id = i.saleId
                     WHERE i.productId = p.id AND s.status = 'COMPLETED' AND s.rev = 0), 0) ELSE 0 END AS stockNowMilli
             FROM products p WHERE p.id = :id""",
    )
    suspend fun stockNow(id: String): ProductStock?

    @Query("SELECT COUNT(*) FROM products WHERE active = 1 AND trackStock = 1 AND (stockMilli < 0 OR (minStockMilli IS NOT NULL AND stockMilli <= minStockMilli))")
    fun reviewCount(): Flow<Int>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY occurredAt DESC LIMIT 100")
    fun movements(productId: String): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovement(m: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovements(items: List<StockMovementEntity>)

    @Query(
        """SELECT COALESCE(SUM(i.quantityMilli), 0) AS quantityMilli,
                  COALESCE(SUM(i.unitPriceMinor * i.quantityMilli / 1000 - i.discountMinor), 0) AS revenueMinor,
                  COALESCE(SUM(CASE WHEN i.unitCostMinor IS NOT NULL THEN i.unitCostMinor * i.quantityMilli / 1000 END), 0) AS costMinor,
                  COALESCE(SUM(CASE WHEN i.unitCostMinor IS NOT NULL THEN i.unitPriceMinor * i.quantityMilli / 1000 - i.discountMinor END), 0) AS costedRevenueMinor
             FROM sale_items i JOIN sales s ON s.id = i.saleId
            WHERE i.productId = :productId AND s.status = 'COMPLETED' AND COALESCE(s.completedAt, s.createdAt) >= :from""",
    )
    fun profit(productId: String, from: Long): Flow<ProductProfit>

    // ---------- proveedores ----------
    @Query("SELECT * FROM suppliers WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun suppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun supplier(id: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuppliers(items: List<SupplierEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplier(s: SupplierEntity)

    // ---------- compras ----------
    @Query(
        """SELECT p.*, COALESCE((SELECT SUM(a.amountMinor) FROM supplier_payments a WHERE a.purchaseId = p.id AND a.voided = 0), 0) AS paidMinor
             FROM purchases p WHERE (:onlyOwed = 0 OR (p.voided = 0 AND p.totalMinor > COALESCE((SELECT SUM(a.amountMinor) FROM supplier_payments a WHERE a.purchaseId = p.id AND a.voided = 0), 0)))
              AND (:supplierId IS NULL OR p.supplierId = :supplierId)
            ORDER BY p.occurredAt DESC LIMIT 200""",
    )
    fun purchases(onlyOwed: Int, supplierId: String?): Flow<List<PurchaseRow>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY position")
    suspend fun items(purchaseId: String): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY position")
    fun itemsFlow(purchaseId: String): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun purchase(id: String): PurchaseEntity?

    @Query("SELECT * FROM supplier_payments WHERE purchaseId = :purchaseId ORDER BY occurredAt")
    fun payments(purchaseId: String): Flow<List<SupplierPaymentEntity>>

    @Query("SELECT * FROM supplier_payments WHERE purchaseId = :purchaseId AND voided = 0")
    suspend fun livePayments(purchaseId: String): List<SupplierPaymentEntity>

    @Query("SELECT * FROM supplier_payments WHERE id = :id")
    suspend fun payment(id: String): SupplierPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchase(p: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchases(items: List<PurchaseEntity>)

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deleteItems(purchaseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PurchaseItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(p: SupplierPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(items: List<SupplierPaymentEntity>)

    /** Lo que se debe a cada proveedor: compras vigentes menos pagos vigentes, sin bajar de cero por compra. */
    @Query(
        """SELECT p.supplierId AS supplierId, SUM(MAX(p.totalMinor - COALESCE((SELECT SUM(a.amountMinor) FROM supplier_payments a WHERE a.purchaseId = p.id AND a.voided = 0), 0), 0)) AS balanceMinor
             FROM purchases p WHERE p.voided = 0 AND p.supplierId IS NOT NULL GROUP BY p.supplierId""",
    )
    fun supplierBalances(): Flow<List<SupplierBalance>>

    @Query(
        """SELECT COALESCE(SUM(MAX(p.totalMinor - COALESCE((SELECT SUM(a.amountMinor) FROM supplier_payments a WHERE a.purchaseId = p.id AND a.voided = 0), 0), 0)), 0)
             FROM purchases p WHERE p.voided = 0""",
    )
    fun totalOwed(): Flow<Long>
}

@Dao
interface NotificationDao {
    @Query(
        """SELECT * FROM notifications WHERE recipientMemberId = :memberId OR recipientDeviceId IS NOT NULL ORDER BY createdAt DESC LIMIT 200""",
    )
    fun inbox(memberId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE readAt IS NULL AND (recipientMemberId = :memberId OR recipientDeviceId IS NOT NULL)")
    fun unreadCount(memberId: String): Flow<Int>

    @Query("SELECT * FROM notifications WHERE readAt IS NULL AND (recipientMemberId = :memberId OR recipientDeviceId IS NOT NULL)")
    suspend fun unread(memberId: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE shown = 0 AND readAt IS NULL AND push = 1 AND (recipientMemberId = :memberId OR recipientDeviceId IS NOT NULL) ORDER BY createdAt")
    suspend fun toShow(memberId: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun get(id: String): NotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(n: NotificationEntity)

    @Query("UPDATE notifications SET shown = 1 WHERE id IN (:ids)")
    suspend fun markShown(ids: List<String>)

    @Query("UPDATE notifications SET readAt = :at WHERE id = :id AND readAt IS NULL")
    suspend fun markRead(id: String, at: Long)

    /** Avisos leídos y viejos no hacen falta en el teléfono. */
    @Query("DELETE FROM notifications WHERE readAt IS NOT NULL AND createdAt < :before")
    suspend fun pruneRead(before: Long)
}

data class SalesTotals(val count: Long, val totalMinor: Long, val discountMinor: Long)
data class MethodAmountRow(val method: String, val amountMinor: Long)
data class ExpenseTotalsSplit(val operatingMinor: Long, val purchasesMinor: Long)
data class TopProductRow(val productId: String?, val name: String, val quantityMilli: Long, val revenueMinor: Long, val profitMinor: Long, val fullyCosted: Boolean)

/**
 * Consultas del resumen. Cada una repite la regla del servidor con enteros (`(precio × cantidad + 500) / 1000` = redondeo hacia arriba en .5, igual que
 * `SaleMath`). Se comprueban EN el teléfono (SQLite) con el mismo escenario que las pruebas del servidor.
 */
@Dao
interface ReportDao {
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(totalMinor), 0) AS totalMinor, COALESCE(SUM(discountMinor), 0) AS discountMinor FROM sales WHERE status = 'COMPLETED' AND completedAt >= :from AND completedAt < :to")
    fun salesTotals(from: Long, to: Long): Flow<SalesTotals>

    @Query(
        """SELECT p.method AS method, COALESCE(SUM(p.amountMinor), 0) AS amountMinor FROM sale_payments p JOIN sales s ON s.id = p.saleId
            WHERE s.status = 'COMPLETED' AND s.completedAt >= :from AND s.completedAt < :to GROUP BY p.method ORDER BY 2 DESC""",
    )
    fun byMethod(from: Long, to: Long): Flow<List<MethodAmountRow>>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN i.unitCostMinor IS NOT NULL THEN (i.unitCostMinor * i.quantityMilli + 500) / 1000 END), 0) AS costOfGoodsMinor,
                  COALESCE(SUM(CASE WHEN i.unitCostMinor IS NOT NULL THEN (i.unitPriceMinor * i.quantityMilli + 500) / 1000 - i.discountMinor END), 0) AS costedRevenueMinor,
                  COALESCE(SUM((i.unitPriceMinor * i.quantityMilli + 500) / 1000 - i.discountMinor), 0) AS lineRevenueMinor
             FROM sale_items i JOIN sales s ON s.id = i.saleId WHERE s.status = 'COMPLETED' AND s.completedAt >= :from AND s.completedAt < :to""",
    )
    fun profitLines(from: Long, to: Long): Flow<com.cuadra.caja.domain.ProfitLines>

    // Un gasto es "compra de mercadería" si su categoría es Mercadería o si nació de un pago a proveedor (mismo id que el pago).
    @Query(
        """SELECT COALESCE(SUM(CASE WHEN e.id IN (SELECT id FROM supplier_payments) OR c.key = 'goods' THEN 0 ELSE e.amountMinor END), 0) AS operatingMinor,
                  COALESCE(SUM(CASE WHEN e.id IN (SELECT id FROM supplier_payments) OR c.key = 'goods' THEN e.amountMinor ELSE 0 END), 0) AS purchasesMinor
             FROM expenses e LEFT JOIN expense_categories c ON c.id = e.categoryId WHERE e.voided = 0 AND e.occurredAt >= :from AND e.occurredAt < :to""",
    )
    fun expenseSplit(from: Long, to: Long): Flow<ExpenseTotalsSplit>

    @Query(
        """SELECT i.productId AS productId, MAX(i.name) AS name, SUM(i.quantityMilli) AS quantityMilli,
                  SUM((i.unitPriceMinor * i.quantityMilli + 500) / 1000 - i.discountMinor) AS revenueMinor,
                  COALESCE(SUM(CASE WHEN i.unitCostMinor IS NOT NULL THEN (i.unitPriceMinor * i.quantityMilli + 500) / 1000 - i.discountMinor - (i.unitCostMinor * i.quantityMilli + 500) / 1000 END), 0) AS profitMinor,
                  MIN(i.unitCostMinor IS NOT NULL) AS fullyCosted
             FROM sale_items i JOIN sales s ON s.id = i.saleId WHERE s.status = 'COMPLETED' AND s.completedAt >= :from AND s.completedAt < :to
            GROUP BY COALESCE(i.productId, 'n:' || i.name) ORDER BY revenueMinor DESC, name LIMIT :limit""",
    )
    fun topProducts(from: Long, to: Long, limit: Int): Flow<List<TopProductRow>>

    @Query("SELECT COALESCE(SUM(balanceMinor), 0) FROM credits WHERE status = 'OPEN'")
    fun receivable(): Flow<Long>
}

/** Guarda personas sin perder el hash del PIN que ya tenemos cuando el dato nuevo llega sin él (ver `mergePinHashes`). */
suspend fun DirectoryDao.mergeMembers(incoming: List<MemberEntity>) {
    val existing = incoming.associate { it.id to member(it.id)?.pinHash }
    upsertMembers(com.cuadra.caja.domain.mergePinHashes(incoming, existing))
}

/** Quita la fila local sin confirmar con ese id (si la hay) y devuelve su fiado, para recalcular su saldo. Se llama dentro de la transacción de la bajada. */
suspend fun CreditDao.deleteUnconfirmedPayment(id: String): String? = unconfirmedPaymentCredit(id)?.also { deleteUnconfirmedPaymentRow(id) }
