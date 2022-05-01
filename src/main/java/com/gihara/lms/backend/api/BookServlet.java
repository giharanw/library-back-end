package com.gihara.lms.backend.api;

import com.gihara.lms.backend.dto.BookDTO;
import com.gihara.lms.backend.exception.ValidationException;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;

@WebServlet(name = "BookServlet", value = {"/books","/books/"})
public class BookServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println(request.getContentType());
        System.out.println(request.getPathInfo());
        System.out.println(getServletInfo());
        doSaveOrUpdate(request, response);
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
        } else if (method.equals("PUT") && !(pathInfo != null && pathInfo.substring(1).matches("\\d+[/]?"))) {
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
                    (book.getIsbn() == null || !book.getIsbn().matches("\\d+"))) {
                throw new ValidationException("Invalid ISBN");
            } else if (book.getName() == null || !book.getName().matches(".+")) {
                throw new ValidationException("Invalid Book Name");
            } else if (book.getAuthor() == null || !book.getAuthor().matches("[A-Za-z0-9 ]+")) {
                throw new ValidationException("Invalid Author Name");
            }
        } catch (ValidationException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
