package jee.wallet.model.ejb;

import jee.wallet.model.entities.User;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.Query;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.LocalBean;

@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class UserEjb extends AbstractEjb implements UserEjbInterface {

    private static final String SELECT_BY_ID = "SELECT u FROM User u WHERE u.id=:id";
    private static final String SELECT_ALL = "SELECT u FROM User u";
    private static final String COUNT_ALL = "SELECT COUNT(u) FROM User u";

    public static String hashString(String s) throws NoSuchAlgorithmException,
            UnsupportedEncodingException {
        MessageDigest mda = MessageDigest.getInstance("SHA-256");
        mda.update(s.getBytes("UTF-8"));

        byte[] hash = mda.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hash.length; i++) {
            sb.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static void hashPassword(User u) throws NoSuchAlgorithmException,
            UnsupportedEncodingException {
        String hash = hashString(u.getPassword());
        if (!StringUtils.equals(hash, u.getPassword())) {
            u.setPassword(hash);
        }
    }

    @Override
    public void create(User user) {
        if (user == null) {
            throw new IllegalArgumentException("The user must be not null.");
        }
        if (em.contains(user)) {
            throw new IllegalStateException("The user is in an invalid state.");
        }

        try {
            UserEjb.hashPassword(user);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing hash algorithm");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Missing hash algorithm");
        }
        em.persist(user);
        em.flush();
    }

    @Override
    public User findById(long id) {
        return (User) em.createQuery(SELECT_BY_ID)
                .setParameter("id", id)
                .getSingleResult();
    }

    @Override
    public List<User> findAll(int offset, int limit) {
        return (List<User>) em.createQuery(SELECT_ALL)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public int countAll() {
        return ((Number) em.createQuery(COUNT_ALL)
                .getSingleResult())
                .intValue();
    }

    private Query createSearchQuery(String prefix, User user) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder sb = new StringBuilder(prefix);
        String separator = " WHERE ";
        if (user != null) {
            if (StringUtils.isNotBlank(user.getUsername())) {
                sb.append(separator).append("c.username = %:username%");
                params.put("username", user.getUsername());
            }
        }

        Query q = em.createQuery(sb.toString());
        setParameters(q, params);
        return q;
    }

    @Override
    public List<User> search(User user, int offset, int limit) {
        return (List<User>) createSearchQuery(SELECT_ALL, user)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public int countSearch(User user) {
        return ((Number) createSearchQuery(COUNT_ALL, user)
                .getSingleResult())
                .intValue();
    }

    @Override
    public void update(User user) {
        if (user == null) {
            throw new IllegalArgumentException("The user must be not null.");
        }
        User c = findById(user.getId());
        if (c == null) {
            throw new IllegalArgumentException("The user is invalid.");
        }
        if (!c.getPassword().equals(user.getPassword())) {
            try {
                hashPassword(user);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Missing hash algorithm");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("Missing hash algorithm");
            }
        }
        em.merge(user);
        em.flush();
    }

    @Override
    public void delete(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("The user does not exist.");
        }
        User user = findById(id);
        delete(user);
    }

    @Override
    public void delete(User user) {
        if (user == null) {
            throw new IllegalArgumentException("The user does not exist.");
        }
        em.remove(user);
        em.flush();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public User login(String username, String password) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT u FROM User u");
        if (StringUtils.isNotBlank(username)) {
            sb.append(" WHERE ").append("u.username = :username ");
            params.put("username", username);
        } else {
            throw new IllegalArgumentException("Username can't be null");
        }
        if (StringUtils.isNotBlank(password)) {
            sb.append(" AND ").append("u.password = :password ");
            try {
                params.put("password", hashString(password));
            } catch (NoSuchAlgorithmException ex) {
                return null;
            } catch (UnsupportedEncodingException ex) {
                return null;
            }
        } else {
            throw new IllegalArgumentException("Password can't be null");
        }
        Query q = em.createQuery(sb.toString());
        setParameters(q, params);
        try {
            return (User) q.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }
}
