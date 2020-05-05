package com.template;

import com.states.OdometerState;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.utilities.NetworkHostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.List;

import static net.corda.core.utilities.NetworkHostAndPort.parse;

/**
 * Connects to a Corda node via RPC and performs RPC operations on the node.
 *
 * The RPC connection is configured using command line arguments.
 */
public class Client {

    public static void main(String[] args) {
        // Create an RPC connection to the node.
        if (args.length != 3) throw new IllegalArgumentException("Usage: Client <node address> <rpc username> <rpc password>");
        final NetworkHostAndPort nodeAddress = parse(args[0]);
        final String rpcUsername = args[1];
        final String rpcPassword = args[2];
        final CordaRPCClient client = new CordaRPCClient(nodeAddress);
        final CordaRPCOps proxy = client.start(rpcUsername, rpcPassword).getProxy();

        // Interact with the node.
        // For example, here we print the nodes on the network.
        final List<NodeInfo> nodes = proxy.networkMapSnapshot();

        System.out.println("XD");
        List<StateAndRef<OdometerState>> states = proxy
                .vaultQueryByCriteria(new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL), OdometerState.class)
                .getStates();

        states.forEach(Client::actionToPerform);


        DataFeed<Vault.Page<OdometerState>, Vault.Update<OdometerState>> dataFeed
                = proxy.vaultTrack(OdometerState.class);
        Observable<Vault.Update<OdometerState>> updates = dataFeed.getUpdates();
        updates.toBlocking().subscribe(update -> update.getProduced().forEach(Client::actionToPerform));
    }

    private static void actionToPerform(StateAndRef<OdometerState> state) {
        System.out.println(state.getState().getData());
    }
}