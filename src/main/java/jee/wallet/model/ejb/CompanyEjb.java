package jee.wallet.model.ejb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import jee.wallet.model.entities.Company;
import jee.wallet.model.entities.History;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ejb.LocalBean;
import javax.persistence.NoResultException;
import jee.wallet.model.entities.OperationType;
import org.apache.commons.io.FileUtils;

@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class CompanyEjb extends AbstractEjb implements CompanyEjbInterface {

    private static final String HISTORY_URL = "http://ichart.finance.yahoo.com/table.csv?s=";

    @EJB
    private HistoryEjb historyEjb;
    @EJB
    private StockOptionEjb stockOptionEjb;
    @EJB
    private StockExchangeEjb stockExchangeEjb;

    private static final String SELECT_BY_ID = "SELECT c FROM Company c WHERE c.id=:id";
    private static final String SELECT_BY_CODE = "SELECT c FROM Company c WHERE c.code=:code";
    private static final String SELECT_ALL = "SELECT c FROM Company c";
    private static final String COUNT_ALL = "SELECT COUNT(c) FROM Company c";

    @Override
    public void create(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("The company must be not null.");
        }
        if (company.getStockExchange() == null) {
            throw new IllegalStateException("The company is in an invalid state.");
        }
        if (em.contains(company)) {
            throw new IllegalStateException("The company is in an invalid state.");
        }

        em.persist(company);
        em.flush();
    }

    @Override
    public Company findById(long id) {
        return (Company) em.createQuery(SELECT_BY_ID)
                .setParameter("id", id)
                .getSingleResult();
    }

    @Override
    public List<Company> findAll(int offset, int limit) {
        return (List<Company>) em.createQuery(SELECT_ALL)
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

    private Query createSearchQuery(String prefix, Company company) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder sb = new StringBuilder(prefix);
        String separator = " WHERE ";
        if (company != null) {
            if (company.getStockExchange() != null) {
                sb.append(" inner join c.stockExchanges se ").append(separator).append(" (");
                separator = "";
                int i = 0;
                sb.append(separator).append("se.id = :seid").append(i);
                params.put("seid" + i, company.getStockExchange().getId());
                separator = " OR ";
                sb.append(") ");
                separator = " AND ";
            }
            if (StringUtils.isNotBlank(company.getCode())) {
                sb.append(separator).append("c.code = :code");
                params.put("code", company.getCode());
                separator = " AND ";
            }
            if (StringUtils.isNotBlank(company.getName())) {
                sb.append(separator).append("c.name = %:name%");
                params.put("name", company.getName());
                separator = " AND ";
            }
            if (StringUtils.isNotBlank(company.getSector())) {
                sb.append(separator).append("c.sector = %:sector%");
                params.put("sector", company.getName());
            }
        }

        Query q = em.createQuery(sb.toString() + " ORDER BY c.code desc");
        setParameters(q, params);
        return q;
    }

    @Override
    public List<Company> search(Company company, int offset, int limit) {
        return (List<Company>) createSearchQuery(SELECT_ALL, company)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public int countSearch(Company company) {
        return ((Number) createSearchQuery(COUNT_ALL, company)
                .getSingleResult())
                .intValue();
    }

    @Override
    public void update(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("The company must be not null.");
        }
        if (company.getStockExchange() == null) {
            throw new IllegalStateException("The company is in an invalid state.");
        }
        if (em.contains(company)) {
            throw new IllegalStateException("The company is in an invalid state.");
        }
        Company c = findById(company.getId());
        if (c == null) {
            throw new IllegalArgumentException("The company is invalid.");
        }

        em.persist(company);
        em.flush();
    }

    @Override
    public void delete(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("The company does not exist.");
        }
        Company company = findById(id);
        delete(company);
    }

    @Override
    public void delete(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("The company does not exist.");
        }
        em.remove(company);
        em.flush();
    }

    @Override
    public void realTimeUpdate(Company company) throws MalformedURLException, IOException {
        URL url = new URL(HISTORY_URL + company.getCode());
        File temp = File.createTempFile("tmp-exchange", ".tmp");
        FileUtils.copyURLToFile(url, temp);
        InputStream in = new FileInputStream(temp);
        BufferedReader buf = new BufferedReader(new InputStreamReader(in));

        company.getHistory().clear();

        String line = buf.readLine();
        while ((line = buf.readLine()) != null) {
            if (StringUtils.isNotBlank(line)) {
                History history = new History(line);
                history.setCompany(company);
                company.getHistory().add(history);
            }
        }

        buf.close();
        temp.deleteOnExit();
        em.merge(company);

        em.flush();
    }

    public Company findByCode(String code) {
        try {
            Company company = (Company) em.createQuery(SELECT_BY_CODE)
                    .setParameter("code", code)
                    .getSingleResult();
            return company;
        } catch (NoResultException e) {
            return null;
        }
    }

    public double getAmountForActions(Company c, double numberOfActions, OperationType type) {
        double amountToWithdraw = c.getLastSale() * numberOfActions;
        if (type == OperationType.PURCHASE) {
            return amountToWithdraw + (TransactionEjb.TRANSACTION_FEES * amountToWithdraw);
        }
        return amountToWithdraw - (TransactionEjb.TRANSACTION_FEES * amountToWithdraw);
    }
}
