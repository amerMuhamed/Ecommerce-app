package com.spring.eCommerce.service.comman;

import java.util.List;

public interface CommonService<REQ, RES> {
    public List<RES> getAll();

    public RES getById(Long id);

    public RES save(REQ obj);

    public void delete(REQ obj);

    public void deleteById(Long id);

    public RES update(Long id, REQ obj);

}

