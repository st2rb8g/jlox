package lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;

    private enum FunctionType{
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum ClassType {
        NONE,
        CLASS
    }


    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    void resolve(List<Stmt> statements) {
        for(Stmt statement: statements) {
            resolve(statement);
        }
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        beginScope();
        FunctionType enclosingFuntion = currentFunction;
        currentFunction = type;
        for(Token param : function.params) {
            declare(param);
            define(param);

        }
        resolve(function.body);
        currentFunction = enclosingFuntion;
        endScope();
    }

    //一个新的块作用域是这样创建的.栈中的每个元素是代表一个块作用域的Map
    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    //退出作用域
    private void endScope() {
        scopes.pop();
    }

    //过在作用域map中将其名称绑定到false来表明该变量“尚未就绪”
    private void declare(Token name) {
        if(scopes.isEmpty()) return;

        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name,
                    "Already a variable with this name in this scope.");
        }//不许重复声明
        scope.put(name.lexeme, false);
    }

    private void define(Token name) {
        if(scopes.isEmpty()) return;

        scopes.peek().put(name.lexeme, true);
    }
    //解析环境
    private void resolveLocal(Expr expr, Token name) {
        for(int i = scopes.size() - 1; i >= 0; i--) {
            if(scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 -i);
                return;
            }
        }
    }

    //这里会开始一个新的作用域，遍历块中的语句，然后丢弃该作用域。
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        declare(stmt.name);
        define(stmt.name);

        beginScope();
        scopes.peek().put("this", true);
        //现在，只要遇到this表达式（至少是在方法内部），
        //它就会解析为一个“局部变量”，该变量定义在方法体块之外的隐含作用域中。

        for(Stmt.Function method: stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }

        endScope();
        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    //动态执行则只会进入正在执行的分支，而静态分析是保守的—它会分析所有可能执行的分支。
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if(stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if(stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword,
                        "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }

        return null;
    }

    //解析函数声明
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }



    //解析变量声明。会在当前最内层的作用域map中添加一个新的条目。
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if(stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        //先解析右值，以防还有对其他变量的引用
        //在解析待赋值的变量
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for(Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    //字面表达式中没有使用任何变量，也不包含任何子表达式，所以也不需要做任何事情。
    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'this' outside of a class.");
            return null;
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if(!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }



}
