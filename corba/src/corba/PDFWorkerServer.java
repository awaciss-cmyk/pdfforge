import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;
import PDFForge.*;

public class PDFWorkerServer {

    public static void main(String[] args) throws Exception {

        // Lire les variables d'environnement pour la config
        String nsHost = System.getenv().getOrDefault("CORBA_NS_HOST", "localhost");
        String nsPort = System.getenv().getOrDefault("CORBA_NS_PORT", "1050");
        String workerHost = System.getenv().getOrDefault("CORBA_WORKER_HOST", "0.0.0.0");
        String workerPort = System.getenv().getOrDefault("CORBA_WORKER_PORT", "1060");

        // Init ORB
        String[] orbArgs = new String[]{
            "-ORBInitialHost", nsHost,
            "-ORBInitialPort", nsPort
        };

        ORB orb = ORB.init(orbArgs, null);
        POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        rootPOA.the_POAManager().activate();

        // Créer et enregistrer le servant
        PDFWorkerImpl worker = new PDFWorkerImpl();
        rootPOA.activate_object(worker);
        org.omg.CORBA.Object ref = rootPOA.servant_to_reference(worker);
        PDFWorker workerRef = PDFWorkerHelper.narrow(ref);

        // Enregistrer dans le Name Service
        org.omg.CORBA.Object nsObj = orb.resolve_initial_references("NameService");
        NamingContextExt ns = NamingContextExtHelper.narrow(nsObj);

        NameComponent[] name = ns.to_name("PDFWorker");
        ns.rebind(name, workerRef);

        System.out.println("✅ PDFWorker CORBA démarré — en attente de requêtes...");
        System.out.println("   NameService : " + nsHost + ":" + nsPort);

        orb.run();
    }
}
