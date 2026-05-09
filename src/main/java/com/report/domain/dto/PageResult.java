package com.report.domain.dto;

import java.util.Collections;
import java.util.List;

public class PageResult<T> {
    private long total;
    private int pageNo;
    private int pageSize;
    private List<T> list;

    public static <T> PageResult<T> empty(int pageNo, int pageSize) {
        PageResult<T> page = new PageResult<T>();
        page.setTotal(0);
        page.setPageNo(pageNo);
        page.setPageSize(pageSize);
        page.setList(Collections.<T>emptyList());
        return page;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
