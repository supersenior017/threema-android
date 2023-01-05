/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.protocol.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.api.work.WorkContact;
import ch.threema.domain.protocol.api.work.WorkData;
import ch.threema.domain.protocol.api.work.WorkDirectory;
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory;
import ch.threema.domain.protocol.api.work.WorkDirectoryFilter;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.testutils.ToStringEqualityArgumentMatcher;

public class APIConnectorTest {
	private @NonNull
	APIConnector getApiConnectorMock() throws NoSuchFieldException, ThreemaException {
		APIConnector connector = mock(APIConnector.class);
		ServerAddressProvider addressProvider = mock(ServerAddressProvider.class);
		when(addressProvider.getDirectoryServerUrl(anyBoolean())).thenReturn("https://server.url/");
		when(addressProvider.getWorkServerUrl(anyBoolean())).thenReturn("https://api-work.threema.ch/");

		setField(connector, APIConnector.class.getDeclaredField("serverAddressProvider"), addressProvider);
		setField(connector, APIConnector.class.getDeclaredField("random"), new SecureRandom());
		return connector;
	}

	private @NonNull
	IdentityStoreInterface getIdentityStoreInterfaceMock() {
		return mock(IdentityStoreInterface.class);
	}

	private void setField(Object object, @NonNull Field field, Object value) {
		try {
			field.setAccessible(true);
			field.set(object, value);
		} catch (IllegalAccessException e) {
			String fieldName = field.getName();
			throw new RuntimeException("Failed to set " + fieldName + " of object", e);
		}
	}

	@Test
	public void testFetchWorkData_Support() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(new PostJsonResult("{\"support\":\"the-support-url\"}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertEquals("the-support-url", result.supportUrl);
		Assert.assertNull(result.logoDark);
		Assert.assertNull(result.logoLight);
		Assert.assertEquals(0, result.workContacts.size());
		Assert.assertNotNull(result.mdm);
		Assert.assertFalse(result.mdm.override);
		Assert.assertNotNull(result.mdm.parameters);
		Assert.assertEquals(0, result.mdm.parameters.size());
	}

	@Test
	public void testFetchWorkData_Logo_Dark() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		JSONObject requiredObject = new JSONObject();
		requiredObject
			.put("username", "u")
			.put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(new PostJsonResult("{\"logo\":{\"dark\": \"the-dark-logo\"}}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertNull(result.supportUrl);
		Assert.assertEquals("the-dark-logo", result.logoDark);
		Assert.assertNull(result.logoLight);
		Assert.assertEquals(0, result.workContacts.size());
		Assert.assertNotNull(result.mdm);
		Assert.assertFalse(result.mdm.override);
		Assert.assertNotNull(result.mdm.parameters);
		Assert.assertEquals(0, result.mdm.parameters.size());
	}

	@Test
	public void testFetchWorkData_Logo_Light() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(new PostJsonResult("{\"logo\":{\"light\": \"the-light-logo\"}}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertNull(result.supportUrl);
		Assert.assertNull(result.logoDark);
		Assert.assertEquals("the-light-logo", result.logoLight);
		Assert.assertEquals(0, result.workContacts.size());
		Assert.assertNotNull(result.mdm);
		Assert.assertFalse(result.mdm.override);
		Assert.assertNotNull(result.mdm.parameters);
		Assert.assertEquals(0, result.mdm.parameters.size());
	}

	@Test
	public void testFetchWorkData_Contacts() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(
			new PostJsonResult(
			"{\"contacts\":[" +
			"{\"id\":\"id1\",\"pk\":\"AQ==\"}," +
			"{\"id\":\"id2\",\"pk\":\"Aq==\",\"first\":\"id2-firstname\"}," +
			"{\"id\":\"id3\",\"pk\":\"Aw==\",\"last\":\"id3-lastname\"}," +
			"{\"id\":\"id4\",\"pk\":\"BA==\",\"first\": \"id4-firstname\", \"last\":\"id4-lastname\"}" +
			"]}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertNull(result.supportUrl);
		Assert.assertNull(result.logoDark);
		Assert.assertNull(result.logoLight);
		Assert.assertEquals(4, result.workContacts.size());
		Assert.assertNotNull(result.mdm);
		Assert.assertFalse(result.mdm.override);
		Assert.assertNotNull(result.mdm.parameters);
		Assert.assertEquals(0, result.mdm.parameters.size());

		// Verify contacts
		Assert.assertEquals("id1", result.workContacts.get(0).threemaId);
		Assert.assertArrayEquals(new byte[]{0x01}, result.workContacts.get(0).publicKey);
		Assert.assertNull(result.workContacts.get(0).firstName);
		Assert.assertNull(result.workContacts.get(0).lastName);

		Assert.assertEquals("id2", result.workContacts.get(1).threemaId);
		Assert.assertArrayEquals(new byte[]{0x02}, result.workContacts.get(1).publicKey);
		Assert.assertEquals("id2-firstname", result.workContacts.get(1).firstName);
		Assert.assertNull(result.workContacts.get(1).lastName);

		Assert.assertEquals("id3", result.workContacts.get(2).threemaId);
		Assert.assertArrayEquals(new byte[]{0x03}, result.workContacts.get(2).publicKey);
		Assert.assertNull(result.workContacts.get(2).firstName);
		Assert.assertEquals("id3-lastname", result.workContacts.get(2).lastName);

		Assert.assertEquals("id4", result.workContacts.get(3).threemaId);
		Assert.assertArrayEquals(new byte[]{0x04}, result.workContacts.get(3).publicKey);
		Assert.assertEquals("id4-firstname", result.workContacts.get(3).firstName);
		Assert.assertEquals("id4-lastname", result.workContacts.get(3).lastName);
	}

	@Test
	public void testFetchWorkData_MDM() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(new PostJsonResult("{\"mdm\":{" +
			"\"override\": true," +
			"\"params\":{" +
				"\"param-string\": \"string-param\"," +
				"\"param-bool\": true," +
				"\"param-int\": 123" +
			"}}}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertNull(result.supportUrl);
		Assert.assertNull(result.logoDark);
		Assert.assertNull(result.logoLight);
		Assert.assertEquals(0, result.workContacts.size());
		Assert.assertNotNull(result.mdm);
		Assert.assertTrue(result.mdm.override);
		Assert.assertNotNull(result.mdm.parameters);
		Assert.assertEquals(3, result.mdm.parameters.size());

		Assert.assertTrue(result.mdm.parameters.containsKey("param-string"));
		Assert.assertEquals("string-param", result.mdm.parameters.get("param-string"));
		Assert.assertTrue(result.mdm.parameters.containsKey("param-bool"));
		Assert.assertEquals(true, result.mdm.parameters.get("param-bool"));
		Assert.assertTrue(result.mdm.parameters.containsKey("param-int"));
		Assert.assertEquals(123, result.mdm.parameters.get("param-int"));
	}

	@Test(expected = JSONException.class)
	public void testFetchWorkContacts_InvalidJSON() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkContacts(any(), any(), any())).thenCallRealMethod();
		when(connector.postJson(
			eq("https://api-work.threema.ch/identities"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn("i-am-not-a-json");
		connector.fetchWorkContacts("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
	}

	@Test
	public void testFetchWorkContacts() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		JSONObject requiredObject = new JSONObject();
		requiredObject
			.put("username", "u")
			.put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkContacts(any(), any(), any())).thenCallRealMethod();
		when(connector.postJson(
			eq("https://api-work.threema.ch/identities"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn("{\"contacts\":[" +
			"{\"id\":\"id1\",\"pk\":\"AQ==\"}," +
			"{\"id\":\"id2\",\"pk\":\"Aq==\",\"first\":\"id2-firstname\"}," +
			"{\"id\":\"id3\",\"pk\":\"Aw==\",\"last\":\"id3-lastname\"}," +
			"{\"id\":\"id4\",\"pk\":\"BA==\",\"first\": \"id4-firstname\", \"last\":\"id4-lastname\"}" +
			"]}");
		List<WorkContact>contacts = connector.fetchWorkContacts("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(contacts);
		Assert.assertEquals(4, contacts.size());

		// Verify contacts
		Assert.assertEquals("id1", contacts.get(0).threemaId);
		Assert.assertArrayEquals(new byte[]{0x01}, contacts.get(0).publicKey);
		Assert.assertNull(contacts.get(0).firstName);
		Assert.assertNull(contacts.get(0).lastName);

		Assert.assertEquals("id2", contacts.get(1).threemaId);
		Assert.assertArrayEquals(new byte[]{0x02}, contacts.get(1).publicKey);
		Assert.assertEquals("id2-firstname", contacts.get(1).firstName);
		Assert.assertNull(contacts.get(1).lastName);

		Assert.assertEquals("id3", contacts.get(2).threemaId);
		Assert.assertArrayEquals(new byte[]{0x03}, contacts.get(2).publicKey);
		Assert.assertNull(contacts.get(2).firstName);
		Assert.assertEquals("id3-lastname", contacts.get(2).lastName);

		Assert.assertEquals("id4", contacts.get(3).threemaId);
		Assert.assertArrayEquals(new byte[]{0x04}, contacts.get(3).publicKey);
		Assert.assertEquals("id4-firstname", contacts.get(3).firstName);
		Assert.assertEquals("id4-lastname", contacts.get(3).lastName);
	}

	@Test
	public void testFetchIdentity() throws Exception {
		final APIConnector connector = getApiConnectorMock();

		when(connector.fetchIdentity(eq("ERIC4911"))).thenCallRealMethod();
		when(connector.doGet(eq("https://server.url/identity/ERIC4911")))
		.thenReturn("{"
				+ "\"identity\": \"ERIC4911\","
				+ "\"publicKey\": \"aGVsbG8=\","
				+ "\"featureLevel\": 3,"
				+ "\"featureMask\": 15,"
				+ "\"state\": 1,"
				+ "\"type\": 2"
			+ "}");
		APIConnector.FetchIdentityResult result = connector.fetchIdentity("ERIC4911");
		Assert.assertNotNull(result);
		Assert.assertEquals("ERIC4911", result.identity);
		Assert.assertEquals(15, result.featureMask);
		Assert.assertEquals(1, result.state);
		Assert.assertEquals(2, result.type);
	}

	@Test
	public void testObtainTurnServers() throws Exception {
		final APIConnector connector = getApiConnectorMock();
		final IdentityStoreInterface identityStore = getIdentityStoreInterfaceMock();

		when(identityStore.getIdentity()).thenReturn("FOOBAR12");
		when(identityStore.calcSharedSecret(any())).thenReturn(new byte[32]);
		System.out.println(identityStore.getIdentity());
		when(connector.obtainTurnServers(eq(identityStore), eq("voip"))).thenCallRealMethod();
		when(connector.postJson(eq("https://server.url/identity/turn_cred"), ArgumentMatchers.any()))
			.thenReturn("{"
				+ "\"token\": \"/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==\","
				+ "\"tokenRespKeyPub\": \"dummy\""
				+ "}")
			.thenReturn("{"
				+ "\"success\": true,"
				+ "\"turnUrls\": [\"turn:foo\", \"turn:bar\"],"
				+ "\"turnUrlsDualStack\": [\"turn:ds-foo\", \"turn:ds-bar\"],"
				+ "\"turnUsername\": \"s00perturnuser\","
				+ "\"turnPassword\": \"t0psecret\","
				+ "\"expiration\": 86400"
				+ "}");

		APIConnector.TurnServerInfo result = connector.obtainTurnServers(identityStore, "voip");
		Assert.assertNotNull(result);
		Assert.assertArrayEquals(new String[] {"turn:foo", "turn:bar"}, result.turnUrls);
		Assert.assertArrayEquals(new String[] {"turn:ds-foo", "turn:ds-bar"}, result.turnUrlsDualStack);
		Assert.assertEquals("s00perturnuser", result.turnUsername);
		Assert.assertEquals("t0psecret", result.turnPassword);

		Date expectedExpirationDate = new Date(new Date().getTime() + 86400*1000);
		Assert.assertTrue(Math.abs(expectedExpirationDate.getTime() - result.expirationDate.getTime()) < 10000);
	}

	@Test
	public void testFetchWorkData_Organization() throws Exception  {
		APIConnector connector = getApiConnectorMock();
		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(new PostJsonResult("{\"org\":{" +
			"\"name\": \"monkeybusiness\"" +
			"}}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.organization);
		Assert.assertEquals("monkeybusiness", result.organization.name);

	}

	@Test
	public void testFetchWorkData_NoOrganization() throws Exception  {
		APIConnector connector = getApiConnectorMock();
		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(new PostJsonResult("{}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.organization);
		Assert.assertNull(result.organization.name);

	}

	@Test
	public void testFetchWorkData_Directory() throws Exception  {
		APIConnector connector = getApiConnectorMock();
		JSONObject requiredObject = new JSONObject();
		requiredObject.put("username", "u").put("password", "eric")
			.put("contacts", (new JSONArray()).put("identity1").put("identity2"));

		when(connector.fetchWorkData(any(), any(), any())).thenCallRealMethod();
		when(connector.postJsonWithResult(
			eq("https://api-work.threema.ch/fetch2"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(new PostJsonResult("{" +
			"directory:{" +
			"enabled: true," +
			"cat: {" +
			"\"c1\": \"Category 1\"," +
			"\"c2\": \"Category 2\"," +
			"\"c3\": \"Category 3\"" +
			"}}}", 0));
		WorkData result = connector.fetchWorkData("u", "eric", new String[]{
			"identity1",
			"identity2"
		});
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.organization);
		Assert.assertNull(result.organization.name);
		Assert.assertTrue(result.directory.enabled);
		Assert.assertNotNull(result.directory.categories);
		Assert.assertEquals(3, result.directory.categories.size());

		boolean c1 = false;
		boolean c2 = false;
		boolean c3 = false;

		for(WorkDirectoryCategory c: result.directory.categories) {
			switch (c.id) {
				case "c1":
					Assert.assertFalse("c1 already found", c1);
					c1 = true;
					Assert.assertEquals("Category 1", c.name);
					break;
				case "c2":
					Assert.assertFalse("c1 already found", c2);
					c2 = true;
					Assert.assertEquals("Category 2", c.name);
					break;
				case "c3":
					Assert.assertFalse("c1 already found", c3);
					c3 = true;
					Assert.assertEquals("Category 3", c.name);
					break;
				default:
					Assert.fail("Invalid category " + c.id);
			}
		}
	}

	@Test
	public void testFetchWorkData_Directory2() throws Exception  {
		APIConnector connector = getApiConnectorMock();
		final IdentityStoreInterface identityStore = getIdentityStoreInterfaceMock();
		when(identityStore.getIdentity()).thenReturn("IDENTITY");
		JSONObject requiredObject = new JSONObject();
		requiredObject
			.put("username", "u")
			.put("password", "eric")
			.put("identity", "IDENTITY")
			.put("query", "Query String")
			.put("categories", (new JSONArray()).put("c100"))
			.put("sort", (new JSONObject())
				.put("asc", true)
				.put("by", "firstName"))
			.put("page", 1);

		when(connector.fetchWorkDirectory(any(), any(), any(), any())).thenCallRealMethod();
		when(connector.postJson(
			eq("https://api-work.threema.ch/directory"),
			argThat(new ToStringEqualityArgumentMatcher<>(requiredObject))
		)).thenReturn(
				"{\n" +
					"   \"contacts\": [\n" +
					"      {\n" +
					"         \"id\": \"ECHOECHO\",\n" +
					"         \"pk\": \"base64\",\n" +
					"         \"first\": \"Hans\",\n" +
					"         \"last\": \"Nötig\",\n" +
					"         \"csi\": \"CSI_NR\",\n" +
					"         \"org\": { \"name\": \"Name der Firma/Organisation\" },\n" +
					"         \"cat\": [\n" +
					"            \"catId1\",\n" +
					"            \"catId2\"\n" +
					"         ]\n" +
					"      }\n" +
					"   ],\n" +
					"   \"paging\": {\n" +
					"      \"size\": 10,\n" +
					"      \"total\": 8923,\n" +
					"      \"next\": 2,\n" +
					"      \"prev\": 0\n" +
					"   }\n" +
					"}"
		);

		WorkDirectoryFilter filter = new WorkDirectoryFilter();
		filter.addCategory(new WorkDirectoryCategory("c100", "Category 100"));
		filter.query("Query String");
		filter.page(1);
		WorkDirectory result = connector.fetchWorkDirectory("u", "eric", identityStore, filter);

		Assert.assertNotNull(result);
		Assert.assertEquals(1, result.workContacts.size());
		Assert.assertEquals("ECHOECHO", result.workContacts.get(0).threemaId);
		Assert.assertEquals("Hans", result.workContacts.get(0).firstName);
		Assert.assertEquals("Nötig", result.workContacts.get(0).lastName);
		Assert.assertEquals("CSI_NR", result.workContacts.get(0).csi);
		Assert.assertEquals("Name der Firma/Organisation", result.workContacts.get(0).organization.name);
		Assert.assertEquals(2, result.workContacts.get(0).categoryIds.size());
		Assert.assertEquals("catId1", result.workContacts.get(0).categoryIds.get(0));
		Assert.assertEquals("catId2", result.workContacts.get(0).categoryIds.get(1));
		Assert.assertEquals(10, result.pageSize);
		Assert.assertEquals(8923, result.totalRecord);
		Assert.assertEquals(2, result.nextFilter.getPage());
		Assert.assertEquals(filter.getQuery(), result.nextFilter.getQuery());
		Assert.assertEquals(filter.getCategories(), result.nextFilter.getCategories());
		Assert.assertEquals(0, result.previousFilter.getPage());
		Assert.assertEquals(filter.getQuery(), result.previousFilter.getQuery());
		Assert.assertEquals(filter.getCategories(), result.previousFilter.getCategories());
	}
}
