package uniandes.unacloud;

import unacloud.share.queue.QueueMessageReceiver;
import unacloud.share.queue.QueueRabbitManager;

import com.losandes.utils.UnaCloudConstants;

import unacloud.share.db.DatabaseConnection;
import unacloud.share.manager.ProjectManager;
import uniandes.unacloud.communication.AgentServerSocket;
import uniandes.unacloud.communication.DataServerSocket;

/**
 * Initializes and control all services in project
 * @author Cesar
 *
 */
public class FileManager extends ProjectManager{
	
	private static FileManager fileManager;
	
	public FileManager() {
		super();
	}
	
	public static FileManager getInstance(){
		try {
			if(fileManager==null)fileManager = new FileManager();
			return fileManager;
		} catch (Exception e) {
			return null;
		}		
	}	

	@Override
	protected String getPortNameVariable() {		
		return UnaCloudConstants.AGENT_PORT;
	}

	@Override
	protected String getPropetiesFileName() {	
		return "grails-app/conf/"+UnaCloudConstants.FILE_CONFIG;
	}

	@Override
	protected String[] getVariableList() {
		return new String[]{UnaCloudConstants.QUEUE_USER,UnaCloudConstants.QUEUE_PASS,UnaCloudConstants.QUEUE_IP,UnaCloudConstants.QUEUE_PORT,UnaCloudConstants.DB_NAME,
				UnaCloudConstants.DB_PASS,UnaCloudConstants.DB_PORT,UnaCloudConstants.DB_IP,UnaCloudConstants.DB_USERNAME,UnaCloudConstants.AGENT_PORT};
	}

	@Override
	protected void startDatabaseService() throws Exception {
		connection = new DatabaseConnection();
		connection.connect(reader.getStringVariable(UnaCloudConstants.DB_NAME), reader.getIntegerVariable(UnaCloudConstants.DB_PORT),
				reader.getStringVariable(UnaCloudConstants.DB_IP), reader.getStringVariable(UnaCloudConstants.DB_USERNAME), reader.getStringVariable(UnaCloudConstants.DB_PASS));
	}

	@Override
	protected void startQueueService() throws Exception {
		QueueRabbitManager rabbitManager = new QueueRabbitManager(reader.getStringVariable(UnaCloudConstants.QUEUE_USER),
				reader.getStringVariable(UnaCloudConstants.QUEUE_PASS), reader.getStringVariable(UnaCloudConstants.QUEUE_IP), 
				reader.getIntegerVariable(UnaCloudConstants.QUEUE_PORT), UnaCloudConstants.QUEUE_FILE);
		queueReceiver = new QueueMessageReceiver();
		queueReceiver.createConnection(rabbitManager);
		queueReceiver.startReceiver(new QueueMessageFileProcessor());	
	}

	@Override
	protected void startCommunicationService() throws Exception {
		new DataServerSocket(reader.getIntegerVariable(UnaCloudConstants.FILE_SERVER_PORT),3).start();
		new AgentServerSocket(reader.getIntegerVariable(UnaCloudConstants.VERSION_MANAGER_PORT), 3).start();
	}

}
