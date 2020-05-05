package com.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.contracts.OdometerContract;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.schemas.OdometerSchema;
import com.states.OdometerState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.FieldInfo;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.getField;

public class UpdateStateFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final int kilometers;
        private final Party manufacturer;
        private final String vin;

        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new odometer");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Veryfying OdometerContract constraints");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the signatures.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION);

        public Initiator(int kilometers, Party manufacturer, String vin) {
            this.kilometers = kilometers;
            this.manufacturer = manufacturer;
            this.vin = vin;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            Party car = getOurIdentity();
            OdometerState newState = new OdometerState(kilometers, vin, car, manufacturer, new UniqueIdentifier());
            StateAndRef<OdometerState> oldState = null;
            QueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
            try {
                FieldInfo carAttribute = getField("car", OdometerSchema.PersistentOdometer.class);
                CriteriaExpression carIndex = Builder.equal(carAttribute, car.getName().toString());
                QueryCriteria customCriteria = new QueryCriteria.VaultCustomQueryCriteria(carIndex);

                QueryCriteria criteria = generalCriteria.and(customCriteria);
                Vault.Page<OdometerState> results = getServiceHub().getVaultService().queryBy(OdometerState.class, criteria);
                List<StateAndRef<OdometerState>> matchingStates = results.getStates();

                if( !matchingStates.isEmpty()){
                    oldState = matchingStates.get(0);
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                return null;
            }

            final Command command;
            final TransactionBuilder builder;

            if(oldState == null){
                command = new Command<>(
                        new OdometerContract.Commands.Create(),
                        ImmutableList.of(newState.getCar().getOwningKey(), newState.getManufacturer().getOwningKey()));
                builder = new TransactionBuilder(notary)
                        .addOutputState(newState, OdometerContract.ID)
                        .addCommand(command);
            }
            else{
                command = new Command<>(
                        new OdometerContract.Commands.Update(),
                        ImmutableList.of(newState.getCar().getOwningKey(), newState.getManufacturer().getOwningKey()));

                builder = new TransactionBuilder(notary)
                        .addInputState(oldState)
                        .addOutputState(newState, OdometerContract.ID)
                        .addCommand(command);
            }

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            builder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(builder);

            progressTracker.setCurrentStep(GATHERING_SIGS);
            FlowSession manufacturerSession = initiateFlow(manufacturer);
            final SignedTransaction signedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx,
                            ImmutableSet.of(manufacturerSession),
                            CollectSignaturesFlow.Companion.tracker()));

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(signedTx, ImmutableSet.of(manufacturerSession)));
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Responder extends FlowLogic<SignedTransaction> {
        private FlowSession manufacturerSession;

        public Responder(FlowSession manufacturerSession) {
            this.manufacturerSession = manufacturerSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            class SignTxFlow extends SignTransactionFlow{

                public SignTxFlow(@NotNull FlowSession otherSideSession, @NotNull ProgressTracker progressTracker) {
                    super(otherSideSession, progressTracker);
                }

                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    requireThat(require ->{
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("Transaction must be Odometer compatible", output instanceof OdometerState);
                        return null;
                    });
                }
            }
            final SignTxFlow signTxFlow = new SignTxFlow(manufacturerSession, SignTransactionFlow.Companion.tracker());
            final SecureHash txId = subFlow(signTxFlow).getId();

            return subFlow(new ReceiveFinalityFlow(manufacturerSession, txId));
        }
    }
}
