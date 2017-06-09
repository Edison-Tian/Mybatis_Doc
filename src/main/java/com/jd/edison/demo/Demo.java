package com.jd.edison.demo;

import com.jd.edison.mybatis.io.Resources;
import com.jd.edison.mybatis.session.SqlSessionFactory;
import com.jd.edison.mybatis.session.SqlSessionFactoryBuilder;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * @author 田继东 on 2017/5/24.
 */
public class Demo {
    public static void main(String[] args) {
        try {
            SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(Resources.getResourceAsReader("mybatis-config.xml"));
            sqlSessionFactory.openSession().select(null,null);
//			InputStream inputStream = new FileInputStream("F:\\soft\\develop\\Maven\\repository\\aopalliance\\aopalliance\\1.0\\_remote.repositories");
//			byte[] result = new byte[4];
//			inputStream.read(result,0,4);
//			for (byte b : result) {
//				System.out.println(b);
//			}
//			byte[] JAR_MAGIC = {'P', 'K', 3, 4};
//			System.out.println(Arrays.equals(JAR_MAGIC,result));
		} catch (IOException e) {
            e.printStackTrace();
        }
    }
}
