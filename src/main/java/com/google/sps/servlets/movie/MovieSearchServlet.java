package com.google.sps.servlets.movie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.sps.KeyConfig;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbSearch;
import info.movito.themoviedbapi.model.core.MovieResultsPage;
import org.json.simple.JSONObject;

import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author chris
 * date: 6/19/2020
 */
@WebServlet("/movies/search")
public class MovieSearchServlet extends HttpServlet {

    private TmdbSearch movieSearchEngine = new TmdbSearch(new TmdbApi(KeyConfig.MOVIE_KEY));
    private Gson gson = new Gson();
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * doGet() handles search queries to tmdb database.
     * @param request: a request may have the following query params: query, pageNumber
     * @param response: a json object returning pagination info and results
     * @throws IOException:
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=utf-8");

        JSONObject json = new JSONObject();
        Integer pageNumber = null;
        String query = null;

        if(null != request.getParameter("pageNumber")) {
            pageNumber = Integer.parseInt(request.getParameter("pageNumber"));
        }

        if(null != request.getParameter("query")) {
            query = request.getParameter("query");
        }

        if(null == request.getParameter("pageNumber") || null == query) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // using 0 for the search year returns all years, can later filter to specific years.
        MovieResultsPage searchResults = movieSearchEngine.searchMovie(query, 0, null, false, pageNumber);

        mapper.writeValue(response.getWriter(), searchResults);
    }
}
