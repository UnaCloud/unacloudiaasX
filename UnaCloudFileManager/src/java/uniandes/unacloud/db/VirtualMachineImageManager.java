package uniandes.unacloud.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.losandes.utils.Constants;

import unacloud.enums.VirtualMachineImageEnum;
import uniandes.unacloud.db.entities.Repository;
import uniandes.unacloud.db.entities.VirtualImageFile;
import db.DatabaseConnection;
import db.VirtualImageManager;

/**
 * Generic class used to create queries and updates for VirtualMachineImage entity
 * Extends from VirtualImageManager to add capacities
 * Added repository queries
 * Added Hypervisor queries
 * @author Cesar
 *
 */
public class VirtualMachineImageManager extends VirtualImageManager{
	
	/**
	 * Method used to return a virtual machine image entity with information about file and repository
	 * @return
	 */
	public static VirtualImageFile getVirtualImageWithFile(Long id, VirtualMachineImageEnum state, boolean withUser){
		try {
			Connection con = DatabaseConnection.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT vm.id, vm.fixed_disk_size, vm.is_public, vm.main_file, vm.repository_id, vm.token, vm.name"+(withUser?",vm.owner_id":"")+" FROM virtual_machine_image vm WHERE vm.state = ? and vm.id = ?;");
			ps.setString(1, state.name());
			ps.setLong(2, id);
			ResultSet rs = ps.executeQuery();			
			if(rs.next()){
				VirtualImageFile image = new VirtualImageFile(rs.getLong(1), state, rs.getString(6), getRepository(rs.getLong(5)), rs.getBoolean(3), rs.getLong(2), rs.getString(4), rs.getString(7));
				if(withUser)image.setOwner(UserManager.getUser(rs.getLong(8)));
				return image;
			}
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Return the main repository in system
	 * @return
	 */
	public static Repository getMainRepository(){
		try {
			Connection con = DatabaseConnection.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT re.id, re.name, re.capacity, re.path FROM repository re WHERE re.name = ?;");
			ps.setString(1, Constants.MAIN_REPOSITORY);
			ResultSet rs = ps.executeQuery();			
			if(rs.next())return new Repository(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4));
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Return a repository entity from database
	 * @param id
	 * @return
	 */
	public static Repository getRepository(Long id){
		try {
			Connection con = DatabaseConnection.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT re.id, re.name, re.capacity, re.path FROM repository re WHERE re.id = ?;");
			ps.setLong(1, id);
			ResultSet rs = ps.executeQuery();			
			if(rs.next())return new Repository(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4));
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Updates a virtual machine image entity on database.
	 * @param machine
	 * @return
	 */
	public static boolean setVirtualMachine(VirtualImageFile image){
		if(image.getId()==null||image.getId()<1)return false;
		try {
			String query = "update virtual_machine_image vm ";
			int isPublic = 0;
			int repository = 0;
			int mainFile = 0;
			int status = 0;
			if(image.isPublic()!=null){query+=" set vm.is_public = ? ";isPublic = 1;}
			if(image.getRepository()!=null){query+=(isPublic>0?",":"")+" set vm.repository_id = ? ";repository = isPublic+1;}
			if(image.getMainFile()!=null){query+=(isPublic>0||repository>0?",":"")+" set vm.main_file = ? ";mainFile = repository>0?repository+1:isPublic+1;}	
			if(image.getState()!=null){query+=(isPublic>0||repository>0||mainFile>0?",":"")+" set vm.state = ? ";status = mainFile>0?mainFile+1:repository>0?repository+1:isPublic+1;}
			if(isPublic>0||repository>0||mainFile>0||status>0){
				query += "where vm.id = ? and vm.id > 0;";
				Connection con = DatabaseConnection.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(query);
				int id = 1;
				if(isPublic>0){ps.setBoolean(isPublic, image.isPublic());id++;}
				if(repository>0){ps.setLong(repository, image.getRepository().getId());id++;}
				if(mainFile>0){ps.setString(mainFile, image.getMainFile());id++;}
				if(status>0){ps.setString(status, image.getState().name());id++;}
				ps.setLong(id, image.getId());
				System.out.println("Change "+ps.executeUpdate()+" lines");
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();			
		}		
		return false;
	}

}
