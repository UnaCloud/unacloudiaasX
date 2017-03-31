package uniandes.unacloud.agent.platform.VirtualBox;

public class VBox5 extends VBoxAPIVersion{

	public static final String VERSION = "5";
	

	@Override
	public String[] createExecutionCommand(String path, String imageName,String command, String username, String password) {		
		return new String[]{path, "--nologo","guestcontrol", imageName, "--username", username, "--password", password, "run", "--exe", command, "--wait-stdout", "--"};
	}

	@Override
	public String[] createCopyToCommand(String path, String imageName,String sourcePath, String guestPath, String username,String password) {		
		return new String[]{path, "--nologo", "guestcontrol", imageName, "--username", username, "--password", password, "copyto", "--target-directory", guestPath, sourcePath};
	}

}
