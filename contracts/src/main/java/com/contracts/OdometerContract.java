package com.contracts;

import com.states.OdometerState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.util.Arrays;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

// ************
// * Contract *
// ************

public class OdometerContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "com.contracts.OdometerContract";

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    @Override
    public void verify(LedgerTransaction tx) {
        final Commands command = tx.findCommand(Commands.class, cmd -> true).getValue();

        if(command instanceof Commands.Create){
            CommandWithParties<Commands.Create> cmd = requireSingleCommand(tx.getCommands(), Commands.Create.class);
            requireThat(require -> {
                require.using("No input should be consumed when creating odometer state", tx.getInputs().isEmpty());
                require.using("Only one state can be created", tx.getOutputs().size() == 1);
                final OdometerState out = tx.outputsOfType(OdometerState.class).get(0);
                require.using("Car and manufacturer can not be the same entity", out.getCar() != out.getManufacturer());
                require.using("Manufacturer and car must be signers", cmd.getSigners().containsAll(out.getParticipants().stream()
                        .map(AbstractParty::getOwningKey)
                        .collect(Collectors.toList())));
                require.using("Current kilometers must be positive", out.getKilometers() > 0);

                return null;
            });
        }
        else if(command instanceof Commands.Update){
            CommandWithParties<Commands.Update> cmd = requireSingleCommand(tx.getCommands(), Commands.Update.class);
            requireThat(require -> {
               require.using("Only one input can be consumed", tx.getInputs().size() == 1);
               require.using("Only one output can be created", tx.getOutputs().size() == 1);
               final OdometerState out = tx.outputsOfType(OdometerState.class).get(0);
               final OdometerState in = tx.inputsOfType(OdometerState.class).get(0);
                require.using("Car and manufacturer can not be the same entity", out.getCar() != out.getManufacturer());
                require.using("Manufacturer and car must be signers", cmd.getSigners().containsAll(out.getParticipants().stream()
                        .map(AbstractParty::getOwningKey)
                        .collect(Collectors.toList())));

                require.using("New odometer state must be higher or equal previous state",
                        in.getKilometers() <= out.getKilometers());

                return null;
            });
        }
    }

    // Used to indicate the transaction's intent.
    public interface Commands extends CommandData {
        class Create implements Commands {}
        class Update implements Commands {}
    }
}