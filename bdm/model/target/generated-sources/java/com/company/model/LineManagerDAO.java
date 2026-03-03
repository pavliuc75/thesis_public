
package com.company.model;

import java.util.List;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;

public interface LineManagerDAO
    extends BusinessObjectDAO
{


    public LineManager findByPersistenceId(Long persistenceId);

    public List<LineManager> findById(Integer id, int startIndex, int maxResults);

    public List<LineManager> findByFullName(String fullName, int startIndex, int maxResults);

    public List<LineManager> findByEmail(String email, int startIndex, int maxResults);

    public List<LineManager> find(int startIndex, int maxResults);

    public Long countForFindById(Integer id);

    public Long countForFindByFullName(String fullName);

    public Long countForFindByEmail(String email);

    public Long countForFind();

    public LineManager newInstance();

}
