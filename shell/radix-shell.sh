INIT=$(cat << EOM
import static com.radixdlt.cli.shell.RadixShell.*;
import com.radixdlt.*;
import com.radixdlt.modules.*;
import com.radixdlt.network.p2p.*;
import com.radixdlt.network.p2p.transport.*;
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

System.out.println("\nWelcome to RadixShell!");
EOM
)

RADIX_NODE_KEYSTORE_PASSWORD=supersecret RADIXDLT_CONSOLE_APPENDER_THRESHOLD=OFF jshell --enable-preview --class-path $(./gradlew -q printShadowJarFilePathForRadixShell) <(echo $INIT) -R -Djava.net.preferIPv4Stack=true
