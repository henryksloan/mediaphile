package com.google.sps.servlets.book;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.books.model.Volumes;
import com.google.gson.Gson;
import com.google.sps.KeyConfig;
import org.json.simple.JSONObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.google.sps.util.Utils;

import com.google.api.services.books.Books;

@WebServlet("/books/search")
public class BookSearchServlet extends HttpServlet {

    private static final long RESULTS_PER_PAGE = 20L;

    private static JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    private Gson gson = new Gson();

    /**
     * doGet() handles search queries to Books database.
     * @param request: a request may have the following query params: query, pageNumber
     * @param response: a json object returning pagination info and results
     * @throws IOException:
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");

        String query = request.getParameter("query");
        if (query == null || query.equals("")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Integer pageNumber = Utils.parseInt(request.getParameter("pageNumber"));
        if (pageNumber == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }


        final NetHttpTransport httpTransport;
        try {
            // Can throw an exception if trusted certificate cannot be established
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        }
        catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Books books = new Books.Builder(httpTransport, jsonFactory, null)
                .setApplicationName(KeyConfig.APPLICATION_NAME)
                .build();

        Volumes volumes = books.volumes().list(query)
               .setMaxResults(RESULTS_PER_PAGE)
               .setStartIndex(pageNumber*RESULTS_PER_PAGE)
               .set("country", "US")
               .execute();

        response.getWriter().println(convertResultsToJson(volumes, pageNumber));
    }

    /**
     * convertResultsToJson converts a Volumes object to json to return to an API request
     * @param searchResults volumes results from a given query
     * @return json payload ready to send to user
     */
    private JSONObject convertResultsToJson(Volumes searchResults, int pageNumber) {
        JSONObject json = new JSONObject();

        json.put("results", gson.toJsonTree(searchResults.getItems()));
        // TODO: We should check if there is a parallel for this in Books, but I think it's superfluous to show in general
        // json.put("totalResults", searchResults.getTotalResults());
        // TODO: This doesn't work with the Books API b/c there are practically infinite pages. Maybe limit max pages.
        // json.put("totalPages", searchResults.getTotalPages());
        json.put("page", pageNumber);

        return json;
    }
}