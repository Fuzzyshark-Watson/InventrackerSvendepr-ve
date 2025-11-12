package Fuzzcode.service;

import Fuzzcode.utilities.LoggerHandler;
import Fuzzcode.dao.UserDao;
import Fuzzcode.model.UserRole;
import org.mindrot.jbcrypt.BCrypt;

// UserService.java
public class UserService {
    private final UserDao dao = new UserDao();

    public Integer register(String username, String password, UserRole role) {
        var salt = BCrypt.gensalt();
        var hash = BCrypt.hashpw(password, salt);
        return dao.insertUser(username, hash, salt, role);
    }
    public boolean login(String username, String password) {
        var user = dao.findByUsername(username);
        if (user == null) return false;
        return BCrypt.checkpw(password, user.passwordHash());
    }
}
