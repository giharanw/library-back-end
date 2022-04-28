package com.gihara.lms.backend.api;

import com.gihara.lms.backend.dto.MemberDTO;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import javax.sql.DataSource;
import javax.xml.bind.ValidationException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet(name = "MemberServlet", value = {"/members","/members/"})
public class MemberServlet extends HttpServlet {

    @Resource(name="java:comp/env/jdbc/pool_library")
    private volatile DataSource pool;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getContentType() == null ||
                !request.getContentType().toLowerCase().startsWith("application/json")) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        try {
            Jsonb jsonb = JsonbBuilder.create();
            MemberDTO member = jsonb.fromJson(request.getReader(), MemberDTO.class);

            if (member.getNic() == null || !member.getNic().matches("\\d{9}[Vv]")) {
                throw new ValidationException("Invalid NIC");
            } else if (member.getName() == null || !member.getName().matches("[A-Za-z ]+")) {
                throw new ValidationException("Invalid Name");
            } else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                throw new ValidationException("Invalid Contact");
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE nic=?");
                stm.setString(1,member.getNic());
                ResultSet rst = stm.executeQuery();
                if (rst.next()){
                    response.sendError(HttpServletResponse.SC_CONFLICT, "Member already exists");
                    return;
                }
                stm = connection.prepareStatement("INSERT INTO member (nic, name, contact) VALUES (?,?,?)");

                stm.setString(1, member.getNic());
                stm.setString(2, member.getName());
                stm.setString(3, member.getContact());
                if (stm.executeUpdate() != 1){
                    throw new RuntimeException("Failed to register the member");
                }
                response.setStatus(HttpServletResponse.SC_CREATED);
            }
        } catch (JsonbException | ValidationException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    (e instanceof JsonbException) ? "Invalid JSON" : e.getMessage());
        } catch (Throwable t){
            t.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
