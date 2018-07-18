package uniandes.unacloud.agent.platform.virtualbox;

import static uniandes.unacloud.common.utils.UnaCloudConstants.ERROR_MESSAGE;

import java.io.*;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import uniandes.unacloud.agent.exceptions.PlatformOperationException;
import uniandes.unacloud.agent.exceptions.UnsupportedPlatformException;
import uniandes.unacloud.agent.execution.domain.Execution;
import uniandes.unacloud.agent.execution.domain.ImageCopy;
import uniandes.unacloud.agent.host.system.OSFactory;
import uniandes.unacloud.agent.platform.Platform;
import uniandes.unacloud.agent.utils.AddressUtility;
import uniandes.unacloud.utils.LocalProcessExecutor;

import java.io.File;

/**
 * Implementation of platform abstract class to give support for VirtualBox
 * platform.
 */
public abstract class VirtualBox extends Platform {
	
	private static final String HEADLESS_SERVICE_NAME = "VBoxHeadless";
	
	private static final String VBOX_SERVICE_NAME = "VBoxSVC";

	private static final String LOCKED_BY_SESSION_ERROR="is already locked by a session";

	private static final String NETWORK_ERROR="Nonexistent host networking interface";
	    
	/**
	 * Class constructor
	 * @param path Path to this platform executable
	 * @throws UnsupportedPlatformException 
	 */
	public VirtualBox(String path) {		
		super(path);
	}
   	
    /**
     * Sends a stop command to the platform
     * @param image Image copy to be stopped 
     */
    @Override
    public void stopExecution(ImageCopy image){
		LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "controlvm", image.getImageName(), "poweroff");
        sleep(2000);
    }
    
    /**
     * Registers a virtual machine on the platform
     * @param image Image copy to be registered
     */
    @Override
	public void registerImage(ImageCopy image){

    	sleep(5000);
        LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "registervm", image.getMainFile().getExecutableFile().getPath());
        sleep(15000);
    }
    
    /**
     * Unregisters a virtual machine from the platform
     * @param image Image copy to be unregistered
     */
    @Override
	public void unregisterImage(ImageCopy image){
        LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "unregistervm", image.getImageName());
        sleep(10000);
        String rta = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "closemedium", "disk", image.getMainFile().getFilePath().replaceAll("vbox", "vdi"));
        System.out.println("FULL UNREGISTER OF IMAGE WITH ROUTE " + image.getMainFile().getFilePath() + ": " + rta);
        sleep(5000);        
    }
    
    /**
     * Sends a reset message to the platform
     * @param image Image to be restarted
     */
    @Override
    public void restartExecution(ImageCopy image) throws PlatformOperationException {
        String h = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "controlvm", image.getImageName(), "reset");
        if (h.contains(ERROR_MESSAGE)) 
            throw new PlatformOperationException(h.length() < 100 ? h : h.substring(0, 100));
        
        sleep(30000);
    }
    
    /**
     * Sends a start message to the platform
     * @param image Image to be started
     */
    @Override
	public void startExecution(ImageCopy image) throws PlatformOperationException {
		setPriority(image);
		//Try three times to start headless
		int times=3;
		String h;
		while(times>0)
		{
			h = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "startvm", image.getImageName(), "--type", "headless");
			System.out.println("Start vm headless response "+h);
			if(h.contains(NETWORK_ERROR))
            {
                System.out.println("Change network int");
                changeExecutionMac(image);
                h = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "startvm", image.getImageName(), "--type", "headless");
                System.out.println("After net; Start vm headless response "+h);
            }
            if (!h.contains(ERROR_MESSAGE) && !h.contains(LOCKED_BY_SESSION_ERROR))
				break;
			times--;
			sleep(30000);
		}
		//If it does not work try with an emergency start
		if(times==0)
		{
			h = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "startvm", image.getImageName(), "--type", "emergencystop");
			System.out.println("Start vm emergencystop response "+h);
			times=6;
			String temp;
			if(h.trim().equals(""))
            {
                h=LocalProcessExecutor.executeCommandOutput(getExecutablePath(),"startvm",image.getImageName(),"--type","headless");
                System.out.println("START HEADLESS "+h);
                sleep(30000);
            }
			while(times>0 && h.trim().equals(""))
			{
				sleep(60000);
				temp=LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "list", "runningvms");
				System.out.println("TEMP "+(6-times)+" RUNNING VMS \n"+temp);
				times--;
			}
			//Try to correct network issues if present
			if(h.contains(NETWORK_ERROR))
			{
				System.out.println("Change network int");
				changeExecutionMac(image);
				h = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "startvm", image.getImageName(), "--type", "headless");
                System.out.println("Start vm headless response "+h);
                if (h.contains(ERROR_MESSAGE) || h.contains("error"))
					throw new PlatformOperationException(h.length() < 100 ? h : h.substring(0, 100));
			}
			else if (h.contains(ERROR_MESSAGE) || h.contains("error"))
				throw new PlatformOperationException(h.length() < 100 ? h : h.substring(0, 100));
		}
        sleep(30000);
        try {
        	OSFactory.getOS().setPriorityProcess(HEADLESS_SERVICE_NAME);
		} catch (Exception e) {
			e.printStackTrace();
		}
		sleep(1000);
    }

	private void setPriority(ImageCopy image) throws PlatformOperationException {
		//To correct executions in Vbox 4.3 and forward
    	try {
    		LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "showvminfo", image.getImageName());
    		sleep(1000);
    		OSFactory.getOS().setPriorityProcess(VBOX_SERVICE_NAME);
    		sleep(1000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
    
    /**
     * Changes VM configuration
     * @param cores new number of cores for the VM
     * @param ram new RAM value for the VM
     * @param image Copy to be modified
     */
    @Override
    public void configureExecutionHardware(int cores, int ram, ImageCopy image) throws PlatformOperationException {

    	if (cores != 0 && ram != 0) {
            LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "modifyvm", image.getImageName(), "--memory", ""+ram, "--cpus", ""+cores);
            sleep(20000);
        }
    }

    /**
     * Get UUID form the given file path
     * @param filePath File path of the image
     * @return UUID of the filePath. Null if there is no image with such file path.
     */
	private String getUUID(String filePath) {

    	String uuid=null;
    	String[] datos;
    	System.out.println("File Path "+filePath);
    	String rta= LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "showhdinfo",filePath);
    	System.out.println("VMS LISTED \n"+rta);
    	for(String s:rta.split("\n"))
		{
			datos=s.split(":");
			if(datos[0].trim().equals("UUID"))
			{
				uuid=datos[1].trim();
				break;
			}
		}
    	System.out.println("Found uuid "+uuid);
    	return uuid;

	}

	/**
     * Executes a command to the VM itself
     * @param image copy in which command will be executed
     * @param command command to be executed
     * @param args command arguments 
     */
    @Override
    public void executeCommandOnExecution(ImageCopy image,String command, String... args) throws PlatformOperationException {
        List<String> com = new ArrayList<>();
        Collections.addAll(com, createExecutionCommand(getExecutablePath(), image.getImageName(), command, image.getImage().getUsername(), image.getImage().getPassword()));
        Collections.addAll(com, args);
        String h = LocalProcessExecutor.executeCommandOutput(com.toArray(new String[0]));
        if (h.contains(ERROR_MESSAGE)) 
            throw new PlatformOperationException(h.length() < 100 ? h : h.substring(0, 100));
        
        sleep(10000);
    }
    /**
     * Sends a file to the VM itself
     * @param image copy in which file will be pasted
     * @param destinationRoute route in which file will be pasted
     * @param sourceFile file to be copied
     */
    @Override
    public void copyFileOnExecution(ImageCopy image, String destinationRoute, File sourceFile) throws PlatformOperationException {
       	String h = LocalProcessExecutor.executeCommandOutput(createCopyToCommand(getExecutablePath(), image.getImageName(), sourceFile.getAbsolutePath(), destinationRoute, image.getImage().getUsername(), image.getImage().getPassword()));
    	if (h.contains(ERROR_MESSAGE)) 
            throw new PlatformOperationException(h.length() < 100 ? h : h.substring(0, 100));
        
        sleep(10000);
    }
    
    /**
     * Takes a snapshot of the VM
     * @param image copy of the image that will have the new snapshot
     * @param snapshotname 
     */
    @Override
    public void takeExecutionSnapshot(ImageCopy image,String snapshotname) {
        LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "snapshot", image.getImageName(), "take", snapshotname);
        sleep(20000);
    }
    
    /**
     * Deletes a snapshot of the VM
     * @param image copy of the image to delete its snapshot
     * @param snapshotname 
     */
    @Override
    public void deleteExecutionSnapshot(ImageCopy image,String snapshotname) {
        LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "snapshot", image.getImageName(), "delete", snapshotname);
        sleep(20000);
    }
    
    /**
     * Changes the VM MAC address
     * @param image copy to be modified
     */
    @Override
    public void changeExecutionMac(ImageCopy image) throws PlatformOperationException {
    	NetworkInterface ninterface = AddressUtility.getDefaultNetworkInterface();
    	LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "modifyvm", image.getImageName(), "--bridgeadapter1", ninterface.getDisplayName(), "--macaddress1", "auto");
        sleep(20000);        
    }

    /**
     * Restores a VM to its snapshot
     * @param image copy to be reverted
     * @param snapshotname snapshot to which image will be restored
     */
	@Override
	public void restoreExecutionSnapshot(ImageCopy image, String snapshotname) throws PlatformOperationException {
		LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "snapshot", image.getImageName(), "restorecurrent");
        sleep(20000);
	}
	
	/**
	 * Verifies if the VM has the specified snapshot
	 * @param image 
	 * @param snapshotname 
	 */
	@Override
	public boolean existsExecutionSnapshot(ImageCopy image, String snapshotname) throws PlatformOperationException {
		String h = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "snapshot", image.getImageName(), "list");
        sleep(20000);
        return h != null && !h.contains("does not");
	}
	
	/**
	 * Unregisters all VMs from platform
	 */
	public void unregisterAllVms(){
		String[] h = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "list", "vms").split("\n|\r");
		for (String vm : h) {
			LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "unregistervm", vm.split(" ")[1]);
	        sleep(15000);
		}
	}
	
	/**
	 * Clones an image making a new copy
	 * @param source source copy
	 * @param dest empty destination copy
	 */
	@Override
	public void cloneImage(ImageCopy source, ImageCopy dest) {
		LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "clonevm", source.getImageName(), "--snapshot", "unacloudbase", "--name", dest.getImageName(), "--basefolder", dest.getMainFile().getExecutableFile().getParentFile().getParentFile().getAbsolutePath(), "--register");
		sleep(20000);
		takeExecutionSnapshot(dest, "unacloudbase");
        unregisterImage(dest);
	}
	
	@Override
	public List<Execution> checkExecutions(Collection<Execution> executions) {
		List<Execution> executionsToDelete = new ArrayList<Execution>();
		List<String> list = new ArrayList<String>();
		try {
			String[] result = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "list", "runningvms").split("\n|\r");
			for (String vm : result)
				list.add(vm.split(" ")[0].replace("\"", "").trim());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (Execution execution: executions) {
			boolean isRunning = false;
			for (String exeInplatform : list) 
				if (exeInplatform.contains(execution.getImage().getImageName())) {
					isRunning = true;
					break;
				}
			
			if (!isRunning)
				executionsToDelete.add(execution);	
		}
		return executionsToDelete;
	}
		
	/**
	 * Method to create command to be executed in guest machine
	 * @param path : VBoxManage path
	 * @param imageName : image name
	 * @param command : command to be executed in guest
	 * @param username : username in virtual machine
	 * @param password : password for username
	 * @return Array with all command elements
	 */
	public abstract String[] createExecutionCommand(String path, String imageName, String command, String username, String password);
	
	/**
	 * Method to create command to copy files in guest machine
	 * @param path : VBoxManage path
	 * @param imageName : image name
	 * @param sourcePath : file path to be copied in guest
	 * @param guestPath : file path to be replaced in guest
	 * @param username : username in virtual machine
	 * @param password : password for username
	 * @return Array with all command elements
	 */
	public abstract String[] createCopyToCommand(String path, String imageName, String sourcePath, String guestPath, String username, String password);

	/**
	 * Configures the image changing the uuid of the given image copy.
	 * @param image Image copy to change the uuid
	 */
	public synchronized void configureImage(ImageCopy image)
	{
		System.out.println("Original path: " +image.getMainFile().getFilePath()+" "+image.getMainFile().getExecutableFile().getAbsolutePath());
		String oldUUID= getUUID(image.getMainFile().getFilePath().replaceAll(".vbox",".vdi"));
		String newUUID="";
		String machineUUID="";
		if(oldUUID!=null)
		{
			try
			{
				//Get id for machine
				machineUUID=LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "internalcommands", "sethduuid",
						image.getMainFile().getFilePath().replaceAll(".vbox", ".vdi")).split(":")[1].trim();
				//Get id for image
				newUUID=LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "internalcommands", "sethduuid",
						image.getMainFile().getFilePath().replaceAll(".vbox", ".vdi")).split(":")[1].trim();
				//Replace files on xml
				replaceUIID(oldUUID,machineUUID,newUUID,image.getMainFile().getFilePath());
                String rta = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "list", "vms");
				System.out.println("LIST BEFORE DEL "+rta);
				rta = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "closemedium", "disk",oldUUID);
				System.out.println("CLOSE MED "+rta);
				rta = LocalProcessExecutor.executeCommandOutput(getExecutablePath(), "list", "vms");
				System.out.println("LIST AFTER DEL "+rta);
			}
			catch(Exception e)
			{
				System.out.println("There was an error replacing UUID "+oldUUID+" with "+newUUID+" "+e.getMessage());
			}
		}

	}

    /**
     * In the .vbox file replace the image UUID (old) with a newly generated one (newUUID) and the machine UUID with another nwely created one (machineUUID) in the given path.
     * @param old Previous image UUID
     * @param machineUUID New machine UUID
     * @param newUUID New image UUID
     * @param path Oath of the vbox
     * @throws Exception If there are errors in the I/O processes
     */
	private void replaceUIID(String old, String machineUUID, String newUUID, String path) throws Exception {
		System.out.println("Replace UUID "+old+" with "+newUUID+" and machine "+machineUUID+" in "+path);
		String remplazo="";
		BufferedReader br=new BufferedReader(new FileReader(new File(path)));
		String linea=br.readLine();
		boolean oldExists=false;
		boolean machineReplaced=false;
		while(linea!=null) {
			if(linea.contains(old))
				oldExists=true;
			if(!machineReplaced && linea.contains("Machine uuid"))
			{
				int firstInd=linea.indexOf("{");
				int lastInd=linea.indexOf("}");
				linea=linea.substring(0,firstInd+1)+machineUUID+linea.substring(lastInd);
			}
			linea=linea.replaceAll(old,newUUID);
			remplazo+=linea+"\n";
			linea=br.readLine();

		}
		br.close();
		System.out.println("EXISTENCIA DE IMG "+oldExists+":"+old+":");
		if(oldExists && !old.trim().equals(""))
		{
			System.out.println("REMPLAZO\n"+remplazo);
			PrintWriter pw=new PrintWriter(new File(path));
			pw.println(remplazo);
			pw.close();
		}
	}

}