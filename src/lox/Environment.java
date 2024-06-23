package lox;

import java.io.ObjectStreamException;
import java.util.HashMap;
import java.util.Map;

public class Environment {
    final Environment enclosing;//在每个环境中添加一个对其外围环境的引用
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }
    /*
        当我们查找一个变量时，我们从最内层开始遍历环境链直到找到该变量。
        从内部作用域开始，就是我们使局部变量遮蔽外部变量的方式。
        无参构造函数用于全局作用域环境，它是环境链的结束点。
        另一个构造函数用来创建一个嵌套在给定外部作用域内的新的局部作用域。
    */

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    Environment ancestor(int distance) {
        Environment environment = this;
        for(int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        return environment;
    }

    Object get(Token name) {
        if(values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        if(enclosing != null) return enclosing.get(name);
        /*
        如果当前环境中没有找到变量，就在外围环境中尝试。然后递归地重复该操作，最终会遍历完整个链路。
         */

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");

    }

    void assign(Token name, Object value) {
        if(values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }
}
