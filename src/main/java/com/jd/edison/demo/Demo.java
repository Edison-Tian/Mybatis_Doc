package com.jd.edison.demo;

import com.jd.edison.mybatis.io.Resources;
import com.jd.edison.mybatis.session.SqlSessionFactory;
import com.jd.edison.mybatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;

/**
 * @author 田继东 on 2017/5/24.
 */
public class Demo {
    public static void main(String[] args) {
        try {
            SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(Resources.getResourceAsReader("mybatis-config.xml"));
            sqlSessionFactory.openSession().select(null,null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
