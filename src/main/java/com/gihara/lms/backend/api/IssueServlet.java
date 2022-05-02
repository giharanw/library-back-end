package com.gihara.lms.backend.api;

import com.gihara.lms.backend.dto.IssueDTO;
import com.gihara.lms.backend.exception.ValidationException;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;

@WebServlet(name = "IssueServlet", value = {"/issues", "/issues/"})
public class IssueServlet extends HttpServlet {
    @Resource(name = "java:comp/env/jdbc/pool4library")
    private volatile DataSource pool;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (req.getContentType() == null || !req.getContentType().startsWith("application/json")){
            resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        try{
            Jsonb jsonb = JsonbBuilder.create();
            IssueDTO issue = jsonb.fromJson(req.getReader(), IssueDTO.class);
            if (issue.getNic() == null || !issue.getNic().matches("\\d{9}[Vv]")){
                throw new ValidationException("Invalid NIC");
            }else if (issue.getIsbn() == null || !issue.getIsbn().matches("\\d{10}")){
                throw new ValidationException("Invalid ISBN");
            }
            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM book INNER JOIN member WHERE nic=? AND isbn=?");
                stm.setString(1, issue.getNic());
                stm.setString(2, issue.getIsbn());
                if (!stm.executeQuery().next()){
                    throw new ValidationException("Invalid NIC or Invalid ISBN");
                }
                stm = connection.prepareStatement("SELECT * FROM issue WHERE isbn = ?");
                stm.setString(1, issue.getIsbn());
                if (stm.executeQuery().next()){
                    resp.sendError(HttpServletResponse.SC_GONE, "Book is not available");
                    return;
                }
                issue.setDate(Date.valueOf(LocalDate.now()));
                stm = connection.prepareStatement("INSERT INTO issue (nic, isbn, date) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS);
                stm.setString(1, issue.getNic());
                stm.setString(2, issue.getIsbn());
                stm.setDate(3, issue.getDate());
                if (stm.executeUpdate() != 1){
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to place the issue");
                    return;
                }
                ResultSet generatedKeys = stm.getGeneratedKeys();
                generatedKeys.next();
                issue.setId(generatedKeys.getInt(1));

                resp.setContentType("application/json");
                resp.setStatus(HttpServletResponse.SC_CREATED);
                jsonb.toJson(issue, resp.getWriter());
            }
        }catch(JsonbException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON");
        }catch (ValidationException e){
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }catch (Throwable t){
            t.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
