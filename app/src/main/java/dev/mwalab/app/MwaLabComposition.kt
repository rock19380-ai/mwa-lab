package dev.mwalab.app

import android.content.Context
import dev.mwalab.faults.DeterministicFaultEngine
import dev.mwalab.faults.FaultSelectionRepository
import dev.mwalab.faults.PersistentFaultSelectionRepository
import dev.mwalab.faults.PrivatePreferencesFaultSelectionStore
import dev.mwalab.approval.ApprovalCoordinator
import dev.mwalab.transaction.TransactionInspection
import dev.mwalab.transaction.TransactionInspector
import dev.mwalab.transaction.TransactionDiagnosticRepository
import dev.mwalab.transaction.TransactionDiagnosticSettlement
import dev.mwalab.storage.RoomTransactionDiagnosticRepository
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.identity.AndroidKeystoreIdentityRepository
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.protocol.recorder.PersistentProtocolRecorder
import dev.mwalab.protocol.recorder.ProtocolRecorder
import dev.mwalab.signing.LabSigningService
import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.SolanaDevnetRpcGateway
import dev.mwalab.simulation.SimulationDiagnosticSettlement
import dev.mwalab.simulation.SimulationRepository
import dev.mwalab.simulation.TransactionSimulationCoordinator
import dev.mwalab.simulation.TransactionSimulationService
import dev.mwalab.storage.RoomSimulationRepository
import dev.mwalab.session.SessionLifecycleCoordinator
import dev.mwalab.session.SessionRepository
import dev.mwalab.storage.MwaLabDatabase
import dev.mwalab.storage.RoomCapabilitySnapshotRepository
import dev.mwalab.storage.RoomSessionRepository

object MwaLabComposition {
    @Volatile
    private var identityServiceInstance: AndroidKeystoreIdentityRepository? = null

    @Volatile
    private var approvalCoordinatorInstance: ApprovalCoordinator? = null

    @Volatile
    private var devnetRpcGatewayInstance: DevnetRpcGateway? = null

    @Volatile
    private var databaseInstance: MwaLabDatabase? = null

    @Volatile
    private var sessionRepositoryInstance: SessionRepository? = null

    @Volatile
    private var capabilitySnapshotRepositoryInstance: CapabilitySnapshotRepository? = null

    @Volatile
    private var transactionDiagnosticRepositoryInstance: TransactionDiagnosticRepository? = null

    @Volatile
    private var transactionDiagnosticSettlementInstance: TransactionDiagnosticSettlement? = null

    @Volatile private var simulationRepositoryInstance: SimulationRepository? = null
    @Volatile private var simulationDiagnosticSettlementInstance: SimulationDiagnosticSettlement? = null
    @Volatile private var simulationServiceInstance: TransactionSimulationService? = null
    @Volatile private var simulationCoordinatorInstance: TransactionSimulationCoordinator? = null

    @Volatile
    private var protocolRecorderInstance: ProtocolRecorder? = null

    @Volatile
    private var faultSelectionRepositoryInstance: FaultSelectionRepository? = null

    private val faultEngineInstance = DeterministicFaultEngine()

    @Volatile
    private var sessionLifecycleCoordinatorInstance: SessionLifecycleCoordinator? = null

    private fun identityService(context: Context): AndroidKeystoreIdentityRepository =
        identityServiceInstance ?: synchronized(this) {
            identityServiceInstance ?: AndroidKeystoreIdentityRepository(
                context.applicationContext,
            ).also { identityServiceInstance = it }
        }

    fun identityRepository(context: Context): IdentityRepository = identityService(context)

    fun signingService(context: Context): LabSigningService = identityService(context)

    fun transactionInspector(): TransactionInspection = TransactionInspector()

    fun approvalCoordinator(): ApprovalCoordinator =
        approvalCoordinatorInstance ?: synchronized(this) {
            approvalCoordinatorInstance ?: ApprovalCoordinator().also {
                approvalCoordinatorInstance = it
            }
        }

    fun devnetRpcGateway(): DevnetRpcGateway =
        devnetRpcGatewayInstance ?: synchronized(this) {
            devnetRpcGatewayInstance ?: SolanaDevnetRpcGateway().also {
                devnetRpcGatewayInstance = it
            }
        }

    private fun database(context: Context): MwaLabDatabase =
        databaseInstance ?: synchronized(this) {
            databaseInstance ?: MwaLabDatabase.create(context.applicationContext).also {
                databaseInstance = it
            }
        }

    fun sessionRepository(context: Context): SessionRepository =
        sessionRepositoryInstance ?: synchronized(this) {
            sessionRepositoryInstance ?: database(context).let { database ->
                RoomSessionRepository(
                    sessionDao = database.sessionDao(),
                    protocolEventDao = database.protocolEventDao(),
                )
            }.also { sessionRepositoryInstance = it }
        }

    fun capabilitySnapshotRepository(context: Context): CapabilitySnapshotRepository =
        capabilitySnapshotRepositoryInstance ?: synchronized(this) {
            capabilitySnapshotRepositoryInstance ?: RoomCapabilitySnapshotRepository(
                database(context).capabilitySnapshotDao(),
            ).also { capabilitySnapshotRepositoryInstance = it }
        }

    fun transactionDiagnosticRepository(context: Context): TransactionDiagnosticRepository =
        transactionDiagnosticRepositoryInstance ?: synchronized(this) {
            transactionDiagnosticRepositoryInstance ?: RoomTransactionDiagnosticRepository(
                database(context).transactionDiagnosticDao(),
            ).also { transactionDiagnosticRepositoryInstance = it }
        }

    fun transactionDiagnosticSettlement(context: Context): TransactionDiagnosticSettlement =
        transactionDiagnosticSettlementInstance ?: synchronized(this) {
            transactionDiagnosticSettlementInstance ?: TransactionDiagnosticSettlement(
                sessionRepository(context), transactionDiagnosticRepository(context),
            ).also { transactionDiagnosticSettlementInstance = it }
        }

    fun simulationRepository(context: Context): SimulationRepository =
        simulationRepositoryInstance ?: synchronized(this) {
            simulationRepositoryInstance ?: RoomSimulationRepository(
                database(context).simulationResultDao(),
            ).also { simulationRepositoryInstance = it }
        }

    fun simulationDiagnosticSettlement(context: Context): SimulationDiagnosticSettlement =
        simulationDiagnosticSettlementInstance ?: synchronized(this) {
            simulationDiagnosticSettlementInstance ?: SimulationDiagnosticSettlement(
                sessionRepository(context), simulationRepository(context),
            ).also { simulationDiagnosticSettlementInstance = it }
        }

    fun simulationService(): TransactionSimulationService =
        simulationServiceInstance ?: synchronized(this) {
            simulationServiceInstance ?: TransactionSimulationService(devnetRpcGateway()).also {
                simulationServiceInstance = it
            }
        }

    fun simulationCoordinator(context: Context): TransactionSimulationCoordinator =
        simulationCoordinatorInstance ?: synchronized(this) {
            simulationCoordinatorInstance ?: TransactionSimulationCoordinator(
                simulationService(), { result -> simulationDiagnosticSettlement(context).record(result) },
            ).also { simulationCoordinatorInstance = it }
        }

    fun protocolRecorder(context: Context): ProtocolRecorder =
        protocolRecorderInstance ?: synchronized(this) {
            protocolRecorderInstance ?: PersistentProtocolRecorder(
                sessionRepository = sessionRepository(context.applicationContext),
            ).also { protocolRecorderInstance = it }
        }

    fun faultSelectionRepository(context: Context): FaultSelectionRepository =
        faultSelectionRepositoryInstance ?: synchronized(this) {
            faultSelectionRepositoryInstance ?: PersistentFaultSelectionRepository(
                PrivatePreferencesFaultSelectionStore(context.applicationContext),
            ).also { faultSelectionRepositoryInstance = it }
        }

    fun deterministicFaultEngine(): DeterministicFaultEngine = faultEngineInstance

    fun sessionLifecycleCoordinator(context: Context): SessionLifecycleCoordinator =
        sessionLifecycleCoordinatorInstance ?: synchronized(this) {
            sessionLifecycleCoordinatorInstance ?: SessionLifecycleCoordinator(
                sessionRepository = sessionRepository(context.applicationContext),
                protocolRecorder = protocolRecorder(context.applicationContext),
            ).also { sessionLifecycleCoordinatorInstance = it }
        }
}
