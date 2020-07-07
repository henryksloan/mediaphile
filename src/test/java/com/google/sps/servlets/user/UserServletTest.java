package com.google.sps.servlets.user;

import com.google.sps.ContextListener;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.mockito.Mockito;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import static com.googlecode.objectify.ObjectifyService.ofy;

public class UserServletTest extends Mockito {

    private final LocalServiceTestHelper helper =
            new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
    Gson gson = new Gson();

    @BeforeClass
    public static void initialize() {
        new ContextListener().initDbObjects();
    }

    @After
    public void tearDown() {
        helper.tearDown();
        ofy().clear();
    }

    /**
     * Ensure error is thrown when no id is provided
     * @throws IOException
     */
    @Test
    public void testMissingId() throws IOException {
        helper.setEnvIsLoggedIn(false)
                .setUp();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writer.flush();
        when(response.getWriter()).thenReturn(writer);

        new UserServlet().doGet(request, response);
        verify(request, atLeast(1)).getParameter("id");
        writer.flush();

        verify(response, times(1)).sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    /**
     * Ensure error is thrown when the id field is equal to ""
     * @throws IOException
     */
    @Test
    public void testEmptyId() throws IOException {
        helper.setEnvIsLoggedIn(false)
                .setUp();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("id")).thenReturn("");

        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writer.flush();
        when(response.getWriter()).thenReturn(writer);

        new UserServlet().doGet(request, response);
        verify(request, atLeast(1)).getParameter("id");
        writer.flush();

        verify(response, times(1)).sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    /**
     * Retrieve a non-existing user from the Datastore
     * @throws IOException
     */
    @Test
    public void testNonStoredUser() throws IOException {
        helper.setEnvIsLoggedIn(false)
                .setUp();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("id")).thenReturn("123");

        HttpServletResponse response = mock(HttpServletResponse.class);

        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writer.flush();
        when(response.getWriter()).thenReturn(writer);

        new UserServlet().doGet(request, response);
        verify(request, atLeast(1)).getParameter("id");
        writer.flush();

        verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Retrieve an existing user from the Datastore
     * Query from an unauthenticated user
     * Should omit the user's email address
     * @throws IOException
     */
    @Test
    public void testStoredUser() throws IOException {
        helper.setEnvIsLoggedIn(false)
                .setUp();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("id")).thenReturn("123");

        HttpServletResponse response = mock(HttpServletResponse.class);

        UserObject user = new UserObject("123", "test", "test@example.com", "");
        ofy().save().entity(user).now();

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writer.flush();
        when(response.getWriter()).thenReturn(writer);

        new UserServlet().doGet(request, response);
        verify(request, atLeast(1)).getParameter("id");
        writer.flush();

        String expected = gson.toJson(user);
        assertEquals(stringWriter.toString().trim(), expected.trim());
    }

    /**
     * Retrieve an existing user from the Datastore
     * Accessed by an authenticated user different from the search target
     * Should omit the user's email address
     * @throws IOException
     */
    @Test
    public void testStoredUserFromOther() throws IOException {
        Map<String, Object> attr = new HashMap<>();
        attr.put("com.google.appengine.api.users.UserService.user_id_key", "777");
        helper.setEnvEmail("other@example.com")
                .setEnvAttributes(attr)
                .setEnvAuthDomain("example.com")
                .setEnvIsLoggedIn(true)
                .setUp();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("id")).thenReturn("123");

        HttpServletResponse response = mock(HttpServletResponse.class);

        UserObject user = new UserObject("123", "test", "test@example.com", "");
        ofy().save().entity(user).now();

        UserObject expectedUser = new UserObject("123", "test", "", "");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writer.flush();
        when(response.getWriter()).thenReturn(writer);

        new UserServlet().doGet(request, response);
        verify(request, atLeast(1)).getParameter("id");
        writer.flush();

        String expected = gson.toJson(expectedUser);
        assertEquals(stringWriter.toString().trim(), expected.trim());
    }

    /**
     * Retrieve an existing user from the Datastore
     * Instance where the user searches for themselves
     * Response should include their email address
     * @throws IOException
     */
    @Test
    public void testStoredUserFromSelf() throws IOException {
        Map<String, Object> attr = new HashMap<>();
        attr.put("com.google.appengine.api.users.UserService.user_id_key", "123");
        helper.setEnvEmail("test@example.com")
                .setEnvAttributes(attr)
                .setEnvAuthDomain("example.com")
                .setEnvIsLoggedIn(true)
                .setUp();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("id")).thenReturn("123");

        HttpServletResponse response = mock(HttpServletResponse.class);

        UserObject user = new UserObject("123", "test", "test@example.com", "");
        ofy().save().entity(user).now();

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writer.flush();
        when(response.getWriter()).thenReturn(writer);

        new UserServlet().doGet(request, response);
        verify(request, atLeast(1)).getParameter("id");
        writer.flush();

        String expected = gson.toJson(user);
        assertEquals(stringWriter.toString().trim(), expected.trim());
    }
}