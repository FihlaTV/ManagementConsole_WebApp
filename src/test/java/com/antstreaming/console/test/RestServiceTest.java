package com.antstreaming.console.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.antmedia.console.datastore.MapDBStore;
import io.antmedia.console.rest.RestService;
import io.antmedia.rest.model.Result;
import io.antmedia.rest.model.User;
import io.antmedia.rest.model.UserType;

public class RestServiceTest {

	private RestService restService;
	private MapDBStore dbStore;

	private static final Integer logHeaderSize = 11;
	private static final String fileNonExistError = "{\"logs\":\""+"There are no registered logs yet\"}";
	private static final String manyCharError = "{\"logs\":\""+"There are no many Chars in File\"}";
	private static final String defaultLogLocation = "target/test-classes/test.log";
	private static final String writeFileText = "Test Log File String Values Lorem Ipsum Dolor Sit Amet";
	private static final String fileText = "{\"logs\":\""+"Test Log File String Values Lorem Ipsum Dolor Sit Amet\"}";

	
	@Before
	public void before() {
		File f = new File("server.db");
		if (f.exists()) {
			try {
				Files.delete(f.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		restService = new RestService();
		dbStore = new MapDBStore();
		restService.setDataStore(dbStore);
	}

	@After
	public void after() {
		// dbStore.clear();
		// dbStore.close();

		File f = new File("server.db");
		if (f.exists()) {
			try {
				Files.delete(f.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Test
	public void testAddUser() {
		Integer userType = 0;
		String password = "password";
		String userName = "username" + (int) (Math.random() * 1000000000);
		User user = new User(userName, password, UserType.ADMIN);
		Result result = restService.addUser(user);

		// System.out.println("error id: " + result.errorId);
		assertTrue(result.isSuccess());

		user = new User(userName, "second pass", UserType.ADMIN);

		user.setPassword("second pass");
		user.setUserType(UserType.ADMIN);
		result = restService.addUser(user);

		assertFalse(result.isSuccess());

		user.setEmail("ksks" + (int) (Math.random() * 100000));
		user.setPassword("second pass");
		user.setUserType(UserType.ADMIN);
		result = restService.addUser(user);
		// should pass because user type is not important right now
		assertTrue(result.isSuccess());

	}

	private volatile boolean err;

	@Test
	public void testMultipleThreads() {

		Thread thread = null;
		err = false;
		for (int i = 0; i < 10; i++) {
			thread = new Thread() {
				public void run() {

					for (int i = 0; i < 20; i++) {
						try {
							testAddUser();
						} catch (Exception e) {
							e.printStackTrace();
							System.err.println("error--------");
							// fail(e.getMessage());
							err = true;
						} catch (AssertionError error) {
							error.printStackTrace();
							System.err.println("assertion error: " + error);
							// fail(error.getMessage());
							err = true;

						}
					}

				};
			};
			thread.start();
		}

		try {
			/*
			 * while (thread.isAlive()) { Thread.sleep(1000); }
			 */
			thread.join();
			assertFalse(err);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	@Test
	public void testEditUser() {

		Integer userType = 1;
		String password = "password";
		String userName = "username" + (int) (Math.random() * 100000);
		User user = new User(userName, password, UserType.ADMIN);

		Result result = restService.addInitialUser(user);
		assertTrue(result.isSuccess());
		assertEquals(password, dbStore.getUser(userName).getPassword());
		assertEquals((int) userType, dbStore.getUser(userName).getUserType());

		// TODO: open below test

		user.setNewPassword("password2");
		Result result2 = restService.changeUserPasswordInternal(userName, user);
		assertTrue(result2.isSuccess());

		assertEquals(user.getNewPassword(), dbStore.getUser(userName).getPassword());
		// assertEquals((int)userType2, dbStore.getUser(userName).userType);

		user.setPassword(user.getNewPassword());
		user.setNewPassword("12345");
		result2 = restService.changeUserPasswordInternal(userName, user);
		assertTrue(result2.isSuccess());

		assertEquals(user.getNewPassword(), dbStore.getUser(userName).getPassword());

		user.setPassword(user.getNewPassword());
		user.setNewPassword("12345678");
		result2 = restService.changeUserPasswordInternal(userName, user);
		assertTrue(result2.isSuccess());

		assertEquals(user.getNewPassword(), dbStore.getUser(userName).getPassword());

		/*
		 * result = restService.editUser("notexist", password2, userType2);
		 * assertFalse(result.isSuccess());
		 * 
		 * 
		 * result = restService.editUser(userName, password2, 3); //should fail
		 * because user type is 3, it should 0 or 1
		 * assertFalse(result.isSuccess());
		 */

	}

	@Test
	public void testDeleteUser() {
		Integer userType = 0;
		String password = "password";
		String userName = "username" + (int) (Math.random() * 100000);
		User user = new User(userName, password, UserType.ADMIN);
		Result result = restService.addUser(user);
		assertTrue(result.isSuccess());
		assertNotNull(dbStore.getUser(userName));
		// TODO: open below test
		/*
		 * result = restService.deleteUser(userName);
		 * assertTrue(result.isSuccess());
		 * assertNull(dbStore.getUser(userName));
		 */
	}

	@Test
	public void testAuthenticateUser() {
		Integer userType = 1;
		String password = "password";
		String userName = "username" + (int) (Math.random() * 100000);
		User user = new User(userName, password, UserType.ADMIN);
		System.out.println("username: " + userName);
		Result result = restService.addUser(user);
		assertTrue(result.isSuccess());
		assertNotNull(dbStore.getUser(userName));
		// TODO: open below test
		/*
		 * result = restService.authenticateUser(userName, password);
		 * assertTrue(result.isSuccess());
		 * 
		 * result = restService.authenticateUser("nope", password);
		 * assertFalse(result.isSuccess());
		 * 
		 * result = restService.authenticateUser(userName, "nope");
		 * assertFalse(result.isSuccess());
		 */
	}


	@Test
	public void testLogFiles() throws IOException {

		//Create Log Files

		writeUsingFiles(writeFileText);

		//Check Log File Create

		File checkFile = new File(defaultLogLocation);

		assertTrue(checkFile.isFile());

		//Tests Non-Exist File Parameter values in Log Services

		String getNonFileLog = restService.getLogFile(100, "");

		assertEquals(fileNonExistError, getNonFileLog);

		//Test Too Many Char Read log files with logLocation Parameters

		String getManyCharLog = restService.getLogFile(1000, defaultLogLocation);

		assertEquals(manyCharError, getManyCharLog);

		//Test char bytes check log files with logLocation Parameters

		String getByteCheckLog = restService.getLogFile(20, defaultLogLocation);

		assertEquals(20+logHeaderSize, getByteCheckLog.getBytes().length);
		
		//Test char different bytes check log files with logLocation Parameters

		String getByteCheckLog2 = restService.getLogFile(30, defaultLogLocation);

		assertEquals(30+logHeaderSize, getByteCheckLog2.getBytes().length);

		//Test check log file texts with logLocation Parameters

		String getFileTextLog = restService.getLogFile(54, defaultLogLocation);

		assertEquals(fileText, getFileTextLog.toString());
		
		//Remove Log File

		File filePath = new File(defaultLogLocation);

		filePath.delete();

		//Check File is Deleted

		assertFalse(checkFile.isFile());

	}

	private static void writeUsingFiles(String data) {
		try {
			Files.write(Paths.get(defaultLogLocation), data.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
