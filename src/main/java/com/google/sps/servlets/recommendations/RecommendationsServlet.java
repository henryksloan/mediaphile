package com.google.sps.servlets.recommendations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.books.Books;
import com.google.api.services.books.model.Volume;
import com.google.api.services.books.model.Volumes;
import com.google.gson.Gson;
import com.google.sps.KeyConfig;
import com.google.sps.model.results.ResultsObject;
import com.google.sps.servlets.book.BookDetailsServlet;
import com.google.sps.util.Utils;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.sps.util.Utils.ContentType;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbMovies;
import info.movito.themoviedbapi.model.core.MovieResultsPage;

@WebServlet("/recommendations")
public class RecommendationsServlet extends HttpServlet {

    private static final long RESULTS_PER_PAGE = 20L;

    private final TmdbMovies moviesApi = new TmdbMovies(new TmdbApi(KeyConfig.MOVIE_KEY));
    private static final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Gson gson = new Gson();

    /**
     * doGet() returns recommendations relative to a given media item
     * Expects ?mediaType={book | movie}&mediaId={id}
     * Optionally accepts &pageNumber, defaulting to zero
     * pageNumber is zero-indexed for both books and movies, automatically accounting for the 1-indexing of movies
     * Returns error 400 if a parameter is empty or invalid (e.g. "bok")
     * Returns error 400 if there is an error getting recommendations (such as when a book doesn't exist)
     * Simply returns an empty list if the page index is past the last result
     * @param request:  expects mediaType&mediaId, and optionally pageNumber
     * @param response: returns a JSON object of either Volumes or Move
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=utf-8");

        String mediaType = request.getParameter("mediaType");
        String mediaId = request.getParameter("mediaId");
        Integer pageNumber = Utils.parseInt(request.getParameter("pageNumber"));

        Integer error = validateParameters(mediaType, mediaId, pageNumber);
        if (error != null) {
            response.sendError(error);
            return;
        }

        if (pageNumber == null) pageNumber = 0;

        sendRecommendations(mediaType, mediaId, pageNumber, response);
    }

    /**
     * Validates the format and presence of the given parameters
     * @return an error code on failure, null on success
     */
    private Integer validateParameters(String mediaType, String mediaId, Integer pageNumber) {
        if (mediaId == null || mediaId.equals("")
                || !ContentType.isType(mediaType)) {
            return HttpServletResponse.SC_BAD_REQUEST;
        }

        // No error, i.e. good to go
        return null;
    }

    private void sendRecommendations(String mediaType, String mediaId,
                                     int pageNumber, HttpServletResponse response) throws IOException {
        switch (mediaType) {
            case ContentType.MOVIE: {
                sendMovieRecommendations(mediaId, pageNumber, response);
                break;
            }
            case ContentType.BOOK: {
                sendBookRecommendations(mediaId, pageNumber, response);
                break;
            }
        }
    }

    private void sendMovieRecommendations(String movieId, int pageNumber,
                                          HttpServletResponse response) throws IOException {
        Integer movieIdInt = Utils.parseInt(movieId);
        if (movieIdInt == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        try {
            MovieResultsPage results = moviesApi.getRecommendedMovies(movieIdInt, null, pageNumber + 1);

            mapper.writeValue(response.getWriter(), results);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private void sendBookRecommendations(String bookId, int pageNumber,
                                         HttpServletResponse response) throws IOException {
        final NetHttpTransport httpTransport;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        Books books = new Books.Builder(httpTransport, jsonFactory, null)
                .setApplicationName(KeyConfig.APPLICATION_NAME)
                .build();

        Volumes volumes;
        try {
            volumes = books.volumes()
                    .associated()
                    .list(bookId)
                    .set("country", "US")
                    .execute();
        } catch (Exception e) {
            bookRecommendationsFallback(bookId, pageNumber, response, books);
            return;
        }

        try {
            // Associated list is not paginated, so this extracts the right slice
            List<Volume> volList = volumes.getItems();
            int total = volList.size();
            int startIndex = (int) RESULTS_PER_PAGE * pageNumber;
            int endIndex = startIndex + (int) RESULTS_PER_PAGE;
            if (endIndex >= volList.size()) endIndex = volList.size() - 1;
            if (endIndex >= startIndex) {
                volList = volList.subList(startIndex, endIndex);
            } else {
                volList = new ArrayList<>();
            }

            int totalPages = total / ((int) RESULTS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;

            response.getWriter().println(gson.toJsonTree(
                    new ResultsObject<>(volList, total, totalPages, pageNumber)));
        }
        catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private void bookRecommendationsFallback(String bookId, int pageNumber,
                                             HttpServletResponse response, Books books) throws IOException {
        Volume volume;
        try {
            volume = new BookDetailsServlet().getDetails(bookId);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // Forms a query
        // If the book has categories, search by the first category
        // If the book does not have categories, search by the first few (at most 3) words of the title
        List<String> categories = volume.getVolumeInfo().getCategories();
        String query;
        if (categories != null && categories.size() > 0) {
            query = "subject:" + spacesToUnderscores(categories.get(0));
        } else {
            query = getFirstWords(volume.getVolumeInfo().getTitle(), 3);
        }

        Volumes volumes = books.volumes().list(query)
                .setMaxResults(RESULTS_PER_PAGE)
                .setStartIndex(pageNumber*RESULTS_PER_PAGE)
                .set("country", "US")
                .execute();
        List<Volume> items = volumes.getItems();
        items = items.stream()
                .filter(item -> !item.getId().equals(volume.getId()))
                .collect(Collectors.toList());
        System.out.println(items);

        int total = volumes.getTotalItems();
        int totalPages = total / ((int) RESULTS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        try {
            response.getWriter().println(gson.toJsonTree(
                    new ResultsObject<>(items, volumes.getTotalItems(), totalPages, pageNumber)));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    String spacesToUnderscores(String str) {
        return str.replaceAll("\\s", "_");
    }

    String getFirstWords(String str, int n) {
        String[] words = str.split("[\\s-.,!?]+");
        words = Arrays.copyOfRange(words, 0, Math.min(words.length, n));
        return String.join(" ", words);
    }
}