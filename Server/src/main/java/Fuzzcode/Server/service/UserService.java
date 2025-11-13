package Fuzzcode.Server.service;

import Fuzzcode.Server.model.AppUser;
import Fuzzcode.Server.dao.UserDao;
import Fuzzcode.Server.model.UserRole;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;

public class UserService {
    private final UserDao dao = new UserDao();

    public AppUser register(String username, String password, UserRole role) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username must not be blank");

        if (password == null || password.isBlank())
            throw new IllegalArgumentException("password must not be blank");

        AppUser existing = dao.findByUsername(username);
        if (existing != null) {
            throw new IllegalStateException("User '" + username + "' already exists");
        }

        String salt = BCrypt.gensalt();
        String hash = BCrypt.hashpw(password, salt);

        int userId = dao.createUser(username, hash, salt, role);

        return dao.findById(userId);
    }
    public boolean login(String username, String password) {
        var user = dao.findByUsername(username);
        if (user == null) return false;
        return BCrypt.checkpw(password, user.passwordHash());
    }
    public AppUser getByUsername(String username) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username must not be blank");
        return dao.findByUsername(username);
    }
    public AppUser getById(int userId) {
        if (userId <= 0)
            throw new IllegalArgumentException("userId must be positive");
        return dao.findById(userId);
    }
    public List<AppUser> listAll() {
        return dao.listAll();
    }
    public boolean updateRole(int userId, UserRole newRole) {
        return dao.updateRole(userId, newRole);
    }
    public boolean updateUsername(int userId, String newUsername) {
        if (newUsername == null || newUsername.isBlank())
            throw new IllegalArgumentException("username must not be blank");
        return dao.updateUsername(userId, newUsername);
    }
    public boolean updatePassword(int userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank())
            throw new IllegalArgumentException("password must not be blank");
        String newSalt = BCrypt.gensalt();
        String newHash = BCrypt.hashpw(newPassword, newSalt);
        return dao.updatePassword(userId, newHash, newSalt);
    }
    public boolean deleteUser(int userId) {
        return dao.delete(userId);
    }
}
