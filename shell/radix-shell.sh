INIT=$(cat << EOM
import static org.radix.utils.shell.RadixShell.*;
import org.radix.*;
import com.radixdlt.network.p2p.liveness.messages.*;
import com.radixdlt.network.p2p.discovery.messages.*;
import com.radixdlt.network.p2p.*;
import com.radixdlt.network.p2p.transport.*;
import com.radixdlt.ModuleRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.networks.Network;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.middleware2.network.*;
import com.radixdlt.sync.*;
import com.radixdlt.sync.messages.remote.*;
import com.radixdlt.environment.rx.*;
import com.radixdlt.environment.*;
import com.radixdlt.consensus.bft.*;
import com.radixdlt.networks.*;
import com.radixdlt.consensus.sync.*;
import java.net.URI;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.networks.Addressing;
import org.radix.utils.shell.RadixShell;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;

System.out.println("\nWelcome to RadixShell!");
EOM
)

RADIX_NODE_KEYSTORE_PASSWORD=supersecret RADIXDLT_CONSOLE_APPENDER_THRESHOLD=OFF jshell --class-path $(./gradlew -q printShadowJarFilePath) <(echo $INIT) -R -Djava.net.preferIPv4Stack=true
