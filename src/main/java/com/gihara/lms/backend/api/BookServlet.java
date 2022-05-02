package com.gihara.lms.backend.api;

import com.gihara.lms.backend.dto.BookDTO;
import com.gihara.lms.backend.exception.ValidationException;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import javax.sql.DataSource;
import javax.sql.rowset.serial.SerialBlob;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@MultipartConfig(location = "/tmp", maxFileSize = 5 * 1024 * 1024)
@WebServlet(name = "BookServlet", value = {"/books","/books/*"})
public class BookServlet extends HttpServlet {

    @Resource(name = "java:comp/env/jdbc/pool_library")
    private volatile DataSource pool;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() != null && !req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String query = req.getParameter("q");
        query = "%" + ((query == null) ? "" : query) + "%";
        try (Connection connection = pool.getConnection()) {
            boolean pagination = req.getParameter("page") != null &&
                    req.getParameter("size") != null;
            String sql = "SELECT * FROM book WHERE isbn LIKE ? OR name LIKE ? OR author LIKE ? " + ((pagination) ? "LIMIT ? OFFSET ?": "");
            PreparedStatement stm = connection.prepareStatement(sql);
            PreparedStatement stmCount = connection.prepareStatement("SELECT * FROM book WHERE isbn LIKE ? OR name LIKE ? OR author LIKE ?");
            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stmCount.setString(1, query);
            stmCount.setString(2, query);
            stmCount.setString(3, query);
            if (pagination){
                int page = Integer.parseInt(req.getParameter("page"));
                int size = Integer.parseInt(req.getParameter("size"));
                stm.setInt(4, size);
                stm.setInt(5, (page - 1) * size);
            }
            ResultSet rst = stm.executeQuery();
            List<BookDTO> books = new ArrayList<>();
            while (rst.next()) {
                books.add((new BookDTO(
                        rst.getString("isbn"),
                        rst.getString("name"),
                        rst.getString("author"),
                        rst.getBytes("preview")
                )));
            }
            ResultSet rst2 = stmCount.executeQuery();
            if (rst2.next()){
                resp.setHeader("X-Count", rst2.getString(1));
            }
            resp.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books, resp.getWriter());

        } catch (SQLException t) {
            t.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doSaveOrUpdate(req, resp);

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doSaveOrUpdate(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() == null || req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unable to delete all books yet");
            return;
        } else if (req.getPathInfo() != null &&
                !req.getPathInfo().substring(1).matches("\\d+[/]?")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Book does not exist");
            return;
        }

        String isbn = req.getPathInfo().replaceAll("[/]", "");

        try (Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.
                    prepareStatement("SELECT * FROM book WHERE isbn=?");
            stm.setString(1, isbn);
            ResultSet rst = stm.executeQuery();

            if (rst.next()) {
                stm = connection.prepareStatement("DELETE FROM book WHERE isbn=?");
                stm.setString(1, isbn);
                if (stm.executeUpdate() != 1) {
                    throw new RuntimeException("Failed to delete the book");
                }
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Book does not exist");
            }
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void doSaveOrUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getContentType()==null || !request.getContentType().toLowerCase().startsWith("multipart/form-data")) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        String method = request.getMethod();
        String pathInfo = request.getPathInfo();
        if (method.equals("POST") && (pathInfo!=null && !pathInfo.equals("/"))){
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } else if (method.equals("PUT") && !(pathInfo != null && pathInfo.substring(1).matches("\\d{10}[/]?"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,"Book does not exist");
            return;
        }
        try{
            String isbn = request.getParameter("isbn");
            String name = request.getParameter("name");
            String author = request.getParameter("author");
            Part preview = request.getPart("preview");
            BookDTO book;
            if (preview != null && !preview.getSubmittedFileName().isEmpty()){
                if (!preview.getContentType().toLowerCase().startsWith("image/")) {
                    throw new ValidationException("Invalid preview");
                }
                byte[] buffer = new byte[(int) preview.getSize()];
                preview.getInputStream().read(buffer);
                book = new BookDTO(isbn, name, author, buffer);
            } else {
                book = new BookDTO(isbn, name, author);
            }
            if (method.equals("POST") &&
                    (book.getIsbn() == null || !book.getIsbn().matches("\\d{10}"))) {
                throw new ValidationException("Invalid ISBN");
            } else if (book.getName() == null || !book.getName().matches(".+")) {
                throw new ValidationException("Invalid Book Name");
            } else if (book.getAuthor() == null || !book.getAuthor().matches("[A-Za-z0-9 ]+")) {
                throw new ValidationException("Invalid Author Name");
            }
            if (method.equals("PUT")) {
                book.setIsbn(pathInfo.replaceAll("[/]", ""));
            }
            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.
                        prepareStatement("SELECT * FROM book WHERE isbn=?");
                stm.setString(1, book.getIsbn());
                ResultSet rst = stm.executeQuery();

                if (rst.next()) {
                    if (method.equals("POST")) {
                        response.sendError(HttpServletResponse.SC_CONFLICT, "Book already exists");
                    } else {
                        stm = connection.
                                prepareStatement("UPDATE book SET name=?, author=?, preview=? WHERE isbn=?");
                        stm.setString(1, book.getName());
                        stm.setString(2, book.getAuthor());
                        stm.setBlob(3, book.getPreview() != null ? new SerialBlob(book.getPreview()) : null);
                        stm.setString(4, book.getIsbn());
                        if (stm.executeUpdate() != 1) {
                            throw new RuntimeException("Failed to update the book details");
                        }
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                } else {
                    stm = connection.prepareStatement("INSERT INTO book (isbn, name, author, preview) VALUES (?,?,?,?)");
                    stm.setString(1, book.getIsbn());
                    stm.setString(2, book.getName());
                    stm.setString(3, book.getAuthor());
                    stm.setBlob(4, book.getPreview() == null ? null : new SerialBlob(book.getPreview()));
                    if (stm.executeUpdate() != 1) {
                        throw new RuntimeException("Failed to add a book");
                    }
                    response.setStatus(HttpServletResponse.SC_CREATED);
                }
            }
        } catch (ValidationException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
