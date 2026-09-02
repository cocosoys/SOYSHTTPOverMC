package com.dlz.db.service.impl;

import com.dlz.db.core.ISqlExecutor;
import com.dlz.db.service.ICommService;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CommServiceImpl implements ICommService {
    private ISqlExecutor sqlExecutor;

    @Override
    public ISqlExecutor getSqlExecutor() {
        return sqlExecutor;
    }

}
