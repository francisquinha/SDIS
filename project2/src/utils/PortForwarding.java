package utils;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.registry.RegistryListener;
import org.fourthline.cling.support.igd.PortMappingListener;
import org.fourthline.cling.support.model.PortMapping;

public class PortForwarding {
	private static final int REMOTE_OBJECT_COM_PORT = 1100;
	private static final int STANDARD_RMI_PORT = 1099;
	private static PortMapping[] desiredMapping = new PortMapping[Constants.MAPPINGS];
	private static UpnpService upnpService = new UpnpServiceImpl();

	public static void doPortForwarding() throws Exception{
		String ip = Utils.getLocalIP();
		desiredMapping[0] = new PortMapping(
				STANDARD_RMI_PORT,
				ip,
				PortMapping.Protocol.TCP
				);

		desiredMapping[1] = new PortMapping(
				REMOTE_OBJECT_COM_PORT,
				ip,
				PortMapping.Protocol.TCP
				);
		RegistryListener registryListener = new PortMappingListener(desiredMapping);
		upnpService.getRegistry().addListener(registryListener);

		upnpService.getControlPoint().search();
	}

	public static void shutdown(){ // When you no longer need the port mapping
		upnpService.shutdown();
	}

}
