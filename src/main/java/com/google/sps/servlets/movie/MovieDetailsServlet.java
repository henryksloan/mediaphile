package com.google.sps.servlets.movie;

import com.google.gson.Gson;
import com.google.sps.KeyConfig;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbMovies;
import info.movito.themoviedbapi.TmdbSearch;
import info.movito.themoviedbapi.model.MovieDb;
import info.movito.themoviedbapi.model.core.MovieResultsPage;
import org.json.simple.JSONObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author chris
 * date: 6/21/2020
 */
@WebServlet("/movies/details")
public class MovieDetailsServlet extends HttpServlet {

    private TmdbMovies moviesQuery = new TmdbMovies(new TmdbApi(KeyConfig.MOVIE_KEY));
    private Gson gson = new Gson();

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=utf-8");

        Integer id = null;

        if(null != request.getParameter("id")) {
            id = Integer.parseInt(request.getParameter("id"));
        }

        if(null == id) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        // Uses null to default language to en-US
        MovieDb queryResponse = moviesQuery.getMovie(id, null);

        response.getWriter().println(gson.toJsonTree(queryResponse));
    }
}
