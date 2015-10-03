package nez.ast.script.asm;

import javax.lang.model.type.NullType;

import nez.ast.TreeVisitor;
import nez.ast.jcode.JCodeOperator;
import nez.ast.script.CommonSymbols;
import nez.ast.script.Hint;
import nez.ast.script.TypeSystem;
import nez.ast.script.TypedTree;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ScriptCompilerAsm extends TreeVisitor implements CommonSymbols {
	private TypeSystem typeSystem;
	private ScriptClassLoader cLoader;
	private ClassBuilder cBuilder;
	private MethodBuilder mBuilder;

	public ScriptCompilerAsm(TypeSystem typeSystem, ScriptClassLoader cLoader) {
		super(TypedTree.class);
		this.typeSystem = typeSystem;
		this.cLoader = cLoader;
	}

	private void visit(TypedTree node) {
		this.visit("visit", node);
	}

	private Class<?> typeof(TypedTree node) {
		// node.getTypedClass();
		return typeSystem.typeof(node);
	}

	private void TODO(String fmt, Object... args) {
		System.err.println("TODO: " + String.format(fmt, args));
	}

	/* typechecker hints */

	private void visitConstantHint(TypedTree node) {
		assert (node.hint() == Hint.Constant);
		Object v = node.getValue();
		if (v instanceof String) {
			this.mBuilder.push((String) v);
		} else if (v instanceof Integer || v instanceof Character || v instanceof Byte) {
			this.mBuilder.push(((Number) v).intValue());
		} else if (v instanceof Double) {
			this.mBuilder.push(((Double) v).doubleValue());
		} else if (v instanceof Boolean) {
			this.mBuilder.push(((Boolean) v).booleanValue());
		} else if (v instanceof Long) {
			this.mBuilder.push(((Long) v).longValue());
		} else if (v instanceof Type) {
			this.mBuilder.push((Type) v);
		} else if (v == null) {
			this.mBuilder.pushNull();
		} else {
			TODO("FIXME: Constant %s", v.getClass().getName());
		}
	}

	private void visitStaticInvocationHint(TypedTree node) {
		for (TypedTree sub : node) {
			visit(sub);
		}
		Type owner = Type.getType(node.getMethod().getDeclaringClass());
		Method methodDesc = Method.getMethod(node.getMethod());
		this.mBuilder.invokeStatic(owner, methodDesc);
	}

	private void visitApplyHint(TypedTree node) {
		for (TypedTree sub : node.get(_param)) {
			visit(sub);
		}
		Type owner = Type.getType(node.getMethod().getDeclaringClass());
		Method methodDesc = Method.getMethod(node.getMethod());
		this.mBuilder.invokeStatic(owner, methodDesc);
	}

	/* class */

	public void openClass(String name) {
		this.cBuilder = new ClassBuilder("nez/ast/script/" + name, null, null, null);
	}

	public void openClass(String name, Class<?> superClass, Class<?>... interfaces) {
		this.cBuilder = new ClassBuilder("nez/ast/script/" + name, null, superClass, interfaces);
	}

	public Class<?> closeClass() {
		cLoader.setDump(true);
		Class<?> c = cLoader.definedAndLoadClass(this.cBuilder.getQualifiedClassName(), cBuilder.toByteArray());
		this.cBuilder = null; //
		return c;
	}

	/* global variable */

	public Class<?> compileGlobalVariableClass(Class<?> t, String name) {
		this.openClass("G_" + name);
		this.cBuilder.addField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "v", t, null);
		return this.closeClass();
	}

	/* generate function class */

	public Class<?> compileFunctionClass(java.lang.reflect.Method staticMethod) {
		this.openClass("C" + staticMethod.getName(), null, null, konoha.Function.class);
		Class<?> returnType = staticMethod.getReturnType();
		Class<?>[] paramTypes = staticMethod.getParameterTypes();
		this.mBuilder = this.cBuilder.newMethodBuilder(Opcodes.ACC_PUBLIC, returnType, "f", paramTypes);
		int index = 1;
		for (int i = 0; i < paramTypes.length; i++) {
			Type AsmType = Type.getType(paramTypes[i]);
			// this.mBuilder.visitVarInsn(AsmType.getOpcode(ILOAD), index);
			this.mBuilder.loadLocal(index, AsmType);
			index += AsmType.getSize();
		}
		Type owner = Type.getType(staticMethod.getDeclaringClass());
		Method methodDesc = Method.getMethod(staticMethod);
		this.mBuilder.invokeStatic(owner, methodDesc);
		this.mBuilder.returnValue();
		this.mBuilder.endMethod();
		return this.closeClass();
	}

	/* static function */

	public Class<?> compileStaticFuncDecl(String className, TypedTree node) {
		this.openClass(className);
		this.visitFuncDecl(node);
		return this.closeClass();
	}

	public void visitFuncDecl(TypedTree node) {
		// this.mBuilderStack.push(this.mBuilder);
		TypedTree nameNode = node.get(_name);
		TypedTree args = node.get(_param);
		String name = nameNode.toText();
		Class<?> funcType = typeof(nameNode);
		Class<?>[] paramTypes = new Class<?>[args.size()];
		for (int i = 0; i < paramTypes.length; i++) {
			paramTypes[i] = typeof(args.get(i));
		}
		this.mBuilder = this.cBuilder.newMethodBuilder(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, funcType, name, paramTypes);
		this.mBuilder.enterScope();
		for (TypedTree arg : args) {
			this.mBuilder.defineArgument(arg.getText(_name, null), typeof(arg));
		}
		visit(node.get(_body));
		this.mBuilder.exitScope();
		// this.mBuilder.returnValue();
		this.mBuilder.endMethod();
	}

	// FIXME Block scope
	public void visitBlock(TypedTree node) {
		this.mBuilder.enterScope();
		for (TypedTree stmt : node) {
			visit(stmt);
		}
		this.mBuilder.exitScope();
	}

	public void visitVarDecl(TypedTree node) {
		if (node.size() > 1) {
			TypedTree varNode = node.get(_name);
			TypedTree valueNode = node.get(_expr);
			visit(valueNode);
			varNode.setType(typeof(valueNode));
			this.mBuilder.createNewVarAndStore(varNode.toText(), typeof(valueNode));
		}
	}

	public void visitApply(TypedTree node) {
		String name = node.getText(_name, null);
		TypedTree argsNode = node.get(_param);
		Class<?>[] args = new Class<?>[argsNode.size()];
		for (int i = 0; i < args.length; i++) {
			args[i] = typeof(argsNode.get(i));
		}
		java.lang.reflect.Method function = node.getMethod(); // typeSystem.findCompiledMethod(name,
		this.mBuilder.callStaticMethod(function.getDeclaringClass(), function.getReturnType(), function.getName(), args);
	}

	public void visitIf(TypedTree node) {
		visit(node.get(_cond));
		this.mBuilder.push(true);

		Label elseLabel = this.mBuilder.newLabel();
		Label mergeLabel = this.mBuilder.newLabel();

		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, elseLabel);

		// then
		visit(node.get(_then));
		this.mBuilder.goTo(mergeLabel);

		// else
		this.mBuilder.mark(elseLabel);
		visit(node.get(_else));

		// merge
		this.mBuilder.mark(mergeLabel);
	}

	public void visitWhile(TypedTree node) {
		Label beginLabel = this.mBuilder.newLabel();
		Label condLabel = this.mBuilder.newLabel();

		this.mBuilder.goTo(condLabel);

		// Block
		this.mBuilder.mark(beginLabel);
		visit(node.get(_body));

		// Condition
		this.mBuilder.mark(condLabel);
		visit(node.get(_cond));
		this.mBuilder.push(true);

		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, this.mBuilder.EQ, beginLabel);
	}

	public void visitDoWhile(TypedTree node) {
		Label beginLabel = this.mBuilder.newLabel();

		// Do
		this.mBuilder.mark(beginLabel);
		visit(node.get(_body));

		// Condition
		visit(node.get(_cond));
		this.mBuilder.push(true);

		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, this.mBuilder.EQ, beginLabel);
	}

	public void visitFor(TypedTree node) {
		Label beginLabel = this.mBuilder.newLabel();
		Label condLabel = this.mBuilder.newLabel();

		// Initialize
		visit(node.get(_init));

		this.mBuilder.goTo(condLabel);

		// Block
		this.mBuilder.mark(beginLabel);
		visit(node.get(_body));
		visit(node.get(_iter));

		// Condition
		this.mBuilder.mark(condLabel);
		visit(node.get(_cond));
		this.mBuilder.push(true);
		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, MethodBuilder.EQ, beginLabel);
	}

	public void visitAssign(TypedTree node) {
		String name = node.getText(_name, null);
		TypedTree valueNode = node.get(_expr);
		VarEntry var = this.mBuilder.getVar(name);
		visit(valueNode);
		if (var != null) {
			this.mBuilder.storeToVar(var);
		} else {
			this.mBuilder.createNewVarAndStore(name, typeof(valueNode));
		}
	}

	public void visitReturn(TypedTree node) {
		this.visit(node.get(_expr));
		this.mBuilder.returnValue();
	}

	// @Override public void VisitReturnNode(ZReturnNode Node) {
	// if(Node.HasReturnExpr()) {
	// Node.ExprNode().Accept(this);
	// Type type = this.AsmType(Node.ExprNode().Type);
	// this.mBuilder.visitInsn(type.getOpcode(IRETURN));
	// }
	// else {
	// this.mBuilder.visitInsn(RETURN);
	// }
	// }

	public void visitExpression(TypedTree node) {
		this.visit(node.get(0));
	}

	public void visitName(TypedTree node) {
		VarEntry var = this.mBuilder.getVar(node.toText());
		// node.setType(var.getVarClass());
		this.mBuilder.loadFromVar(var);
	}

	private Class<?> typeInfferBinary(TypedTree binary, TypedTree left, TypedTree right) {
		Class<?> leftType = typeof(left);
		Class<?> rightType = typeof(right);
		if (leftType == int.class) {
			if (rightType == int.class) {
				if (binary.getTag().getSymbol().equals("Div")) {
					return double.class;
				}
				return int.class;
			} else if (rightType == double.class) {
				return double.class;
			} else if (rightType == String.class) {
				return String.class;
			}
		} else if (leftType == double.class) {
			if (rightType == int.class) {
				return double.class;
			} else if (rightType == double.class) {
				return double.class;
			} else if (rightType == String.class) {
				return String.class;
			}
		} else if (leftType == String.class) {
			return String.class;
		} else if (leftType == boolean.class) {
			if (rightType == boolean.class) {
				return boolean.class;
			} else if (rightType == String.class) {
				return String.class;
			}
		}
		throw new RuntimeException("type error: " + left + ", " + right);
	}

	public void visitBinaryNode(TypedTree node) {
		TypedTree left = node.get(_left);
		TypedTree right = node.get(_right);
		this.visit(left);
		this.visit(right);
		node.setType(typeInfferBinary(node, left, right));
		this.mBuilder.callStaticMethod(JCodeOperator.class, typeof(node), node.getTag().getSymbol(), typeof(left), typeof(right));
	}

	public void visitCompNode(TypedTree node) {
		TypedTree left = node.get(_left);
		TypedTree right = node.get(_right);
		this.visit(left);
		this.visit(right);
		node.setType(boolean.class);
		this.mBuilder.callStaticMethod(JCodeOperator.class, typeof(node), node.getTag().getSymbol(), typeof(left), typeof(right));
	}

	public void visitAdd(TypedTree node) {
		this.visitBinaryNode(node);
	}

	public void visitSub(TypedTree node) {
		this.visitBinaryNode(node);
	}

	public void visitMul(TypedTree node) {
		this.visitBinaryNode(node);
	}

	public void visitDiv(TypedTree node) {
		this.visitBinaryNode(node);
	}

	public void visitNotEquals(TypedTree node) {
		this.visitCompNode(node);
	}

	public void visitLessThan(TypedTree node) {
		this.visitCompNode(node);
	}

	public void visitLessThanEquals(TypedTree node) {
		this.visitCompNode(node);
	}

	public void visitGreaterThan(TypedTree node) {
		this.visitCompNode(node);
	}

	public void visitGreaterThanEquals(TypedTree node) {
		this.visitCompNode(node);
	}

	public void visitLogicalAnd(TypedTree node) {
		this.visitCompNode(node);
	}

	public void visitLogicalOr(TypedTree node) {
		this.visitCompNode(node);
	}

	//
	// public void visitContinue(TypedTree node) {
	// }
	//
	// public void visitBreak(TypedTree node) {
	// }
	//
	// public void visitReturn(TypedTree node) {
	// this.mBuilder.returnValue();
	// }
	//
	// public void visitThrow(TypedTree node) {
	// }
	//
	// public void visitWith(TypedTree node) {
	// }
	//
	// public void visitExpression(TypedTree node) {
	// this.visit(node.get(0));
	// }
	//

	//
	// public void visitApply(TypedTree node) {
	// // TypedTree fieldNode = node.get(_recv"));
	// TypedTree argsNode = node.get(_param);
	// TypedTree name = node.get(_name);
	// // VarEntry var = null;
	//
	// Class<?>[] argTypes = new Class<?>[argsNode.size()];
	// for (int i = 0; i < argsNode.size(); i++) {
	// TypedTree arg = argsNode.get(i);
	// this.visit(arg);
	// argTypes[i] = arg.getTypedClass();
	// }
	// org.objectweb.asm.commons.Method method =
	// Methods.method(node.getTypedClass(), name.toText(), argTypes);
	// this.mBuilder.invokeStatic(this.cBuilder.getTypeDesc(), method);
	// // var = this.scope.getLocalVar(top.toText());
	// // if (var != null) {
	// // this.mBuilder.loadFromVar(var);
	// //
	// // } else {
	// // this.generateRunTimeLibrary(top, argsNode);
	// // this.popUnusedValue(node);
	// // return;
	// // }
	// }
	//
	// public void generateRunTimeLibrary(TypedTree fieldNode, TypedTree
	// argsNode) {
	// String classPath = "";
	// String methodName = null;
	// for (int i = 0; i < fieldNode.size(); i++) {
	// if (i < fieldNode.size() - 2) {
	// classPath += fieldNode.get(i).toText();
	// classPath += ".";
	// } else if (i == fieldNode.size() - 2) {
	// classPath += fieldNode.get(i).toText();
	// } else {
	// methodName = fieldNode.get(i).toText();
	// }
	// }
	// Type[] argTypes = new Type[argsNode.size()];
	// for (int i = 0; i < argsNode.size(); i++) {
	// TypedTree arg = argsNode.get(i);
	// this.visit(arg);
	// argTypes[i] = Type.getType(arg.getTypedClass());
	// }
	// this.mBuilder.callDynamicMethod("nez/ast/jcode/StandardLibrary",
	// "bootstrap", methodName, classPath, argTypes);
	// }
	//
	// public void visitField(TypedTree node) {
	// TypedTree top = node.get(0);
	// VarEntry var = null;
	// if (_Name.equals(top.getTag())) {
	// var = this.scope.getLocalVar(top.toText());
	// if (var != null) {
	// this.mBuilder.loadFromVar(var);
	// } else {
	// // TODO
	// return;
	// }
	// } else {
	// visit(top);
	// }
	// for (int i = 1; i < node.size(); i++) {
	// TypedTree member = node.get(i);
	// if (_Name.equals(member.getTag())) {
	// this.mBuilder.getField(Type.getType(var.getVarClass()), member.toText(),
	// Type.getType(Object.class));
	// visit(member);
	// }
	// }
	// }
	//
	// //
	// public void visitUnaryNode(TypedTree node) {
	// TypedTree child = node.get(0);
	// this.visit(child);
	// node.setType(this.typeInfferUnary(node.get(0)));
	// this.mBuilder.callStaticMethod(JCodeOperator.class, node.getTypedClass(),
	// node.getTag().getSymbol(), child.getTypedClass());
	// this.popUnusedValue(node);
	// }
	//
	// public void visitPlus(TypedTree node) {
	// this.visitUnaryNode(node);
	// }
	//
	// public void visitMinus(TypedTree node) {
	// this.visitUnaryNode(node);
	// }
	//
	// private void evalPrefixInc(TypedTree node, int amount) {
	// TypedTree nameNode = node.get(0);
	// VarEntry var = this.scope.getLocalVar(nameNode.toText());
	// if (var != null) {
	// node.setType(int.class);
	// this.mBuilder.callIinc(var, amount);
	// if (!node.requiredPop) {
	// this.mBuilder.loadFromVar(var);
	// }
	// } else {
	// throw new RuntimeException("undefined variable " + nameNode.toText());
	// }
	// }
	//
	// private void evalSuffixInc(TypedTree node, int amount) {
	// TypedTree nameNode = node.get(0);
	// VarEntry var = this.scope.getLocalVar(nameNode.toText());
	// if (var != null) {
	// node.setType(int.class);
	// if (!node.requiredPop) {
	// this.mBuilder.loadFromVar(var);
	// }
	// this.mBuilder.callIinc(var, amount);
	// } else {
	// throw new RuntimeException("undefined variable " + nameNode.toText());
	// }
	// }
	//
	// public void visitSuffixInc(TypedTree node) {
	// this.evalSuffixInc(node, 1);
	// }
	//
	// public void visitSuffixDec(TypedTree node) {
	// this.evalSuffixInc(node, -1);
	// }
	//
	// public void visitPrefixInc(TypedTree node) {
	// this.evalPrefixInc(node, 1);
	// }
	//
	// public void visitPrefixDec(TypedTree node) {
	// this.evalPrefixInc(node, -1);
	// }
	//
	// private Class<?> typeInfferUnary(TypedTree node) {
	// Class<?> nodeType = node.getTypedClass();
	// if (nodeType == int.class) {
	// return int.class;
	// } else if (nodeType == double.class) {
	// return double.class;
	// }
	// throw new RuntimeException("type error: " + node);
	// }

	public void visitNull(TypedTree p) {
		p.setType(NullType.class);
		this.mBuilder.pushNull();
	}

	// void visitArray(TypedTree p){
	// this.mBuilder.newArray(Object.class);
	// }

	public void visitList(TypedTree node) {
		for (TypedTree element : node) {
			visit(element);
		}
	}

	public void visitTrue(TypedTree p) {
		p.setType(boolean.class);
		this.mBuilder.push(true);
	}

	public void visitFalse(TypedTree p) {
		p.setType(boolean.class);
		this.mBuilder.push(false);
	}

	public void visitInt(TypedTree p) {
		p.setType(int.class);
		this.mBuilder.push(Integer.parseInt(p.toText()));
	}

	public void visitInteger(TypedTree p) {
		this.visitInt(p);
	}

	public void visitOctalInteger(TypedTree p) {
		p.setType(int.class);
		this.mBuilder.push(Integer.parseInt(p.toText(), 8));
	}

	public void visitHexInteger(TypedTree p) {
		p.setType(int.class);
		this.mBuilder.push(Integer.parseInt(p.toText(), 16));
	}

	public void visitDouble(TypedTree p) {
		p.setType(double.class);
		this.mBuilder.push(Double.parseDouble(p.toText()));
	}

	public void visitString(TypedTree p) {
		p.setType(String.class);
		this.mBuilder.push(p.toText());
	}

	public void visitCharacter(TypedTree p) {
		p.setType(String.class);
		this.mBuilder.push(p.toText());
		// p.setType(char.class);
		// this.mBuilder.push(p.toText().charAt(0));
	}

	public void visitUndefined(TypedTree p) {
		System.out.println("undefined: " + p.getTag().getSymbol());
	}

	/* code copied from libzen */

	// private JavaStaticFieldNode GenerateFunctionAsSymbolField(String
	// FuncName, ZFunctionNode Node) {
	// @Var ZFuncType FuncType = Node.GetFuncType();
	// String ClassName = this.NameFunctionClass(FuncName, FuncType);
	// Class<?> FuncClass = this.LoadFuncClass(FuncType);
	// @Var AsmClassBuilder ClassBuilder =
	// this.AsmLoader.NewClass(ACC_PUBLIC|ACC_FINAL, Node, ClassName,
	// FuncClass);
	//
	// AsmMethodBuilder InvokeMethod = ClassBuilder.NewMethod(ACC_PUBLIC |
	// ACC_FINAL, "Invoke", FuncType);
	// int index = 1;
	// for(int i = 0; i < FuncType.GetFuncParamSize(); i++) {
	// Type AsmType = this.AsmType(FuncType.GetFuncParamType(i));
	// InvokeMethod.visitVarInsn(AsmType.getOpcode(ILOAD), index);
	// index += AsmType.getSize();
	// }
	// InvokeMethod.visitMethodInsn(INVOKESTATIC, ClassName, "f", FuncType);
	// InvokeMethod.visitReturn(FuncType.GetReturnType());
	// InvokeMethod.Finish();
	//
	// ClassBuilder.AddField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "function",
	// FuncClass, null);
	//
	// // static init
	// AsmMethodBuilder StaticInitMethod = ClassBuilder.NewMethod(ACC_PUBLIC |
	// ACC_STATIC , "<clinit>", "()V");
	// StaticInitMethod.visitTypeInsn(NEW, ClassName);
	// StaticInitMethod.visitInsn(DUP);
	// StaticInitMethod.visitMethodInsn(INVOKESPECIAL, ClassName, "<init>",
	// "()V");
	// StaticInitMethod.visitFieldInsn(PUTSTATIC, ClassName, "function",
	// FuncClass);
	// StaticInitMethod.visitInsn(RETURN);
	// StaticInitMethod.Finish();
	//
	// AsmMethodBuilder InitMethod = ClassBuilder.NewMethod(ACC_PRIVATE,
	// "<init>", "()V");
	// InitMethod.visitVarInsn(ALOAD, 0);
	// InitMethod.visitLdcInsn(FuncType.TypeId);
	// InitMethod.visitLdcInsn(FuncName);
	// InitMethod.visitMethodInsn(INVOKESPECIAL,
	// Type.getInternalName(FuncClass), "<init>", "(ILjava/lang/String;)V");
	// InitMethod.visitInsn(RETURN);
	// InitMethod.Finish();
	//
	// AsmMethodBuilder StaticFuncMethod = ClassBuilder.NewMethod(ACC_PUBLIC |
	// ACC_STATIC, "f", FuncType);
	// for(int i = 0; i < Node.GetListSize(); i++) {
	// ZParamNode ParamNode = Node.GetParamNode(i);
	// Class<?> DeclClass = this.GetJavaClass(ParamNode.DeclType());
	// StaticFuncMethod.AddLocal(DeclClass, ParamNode.GetName());
	// }
	// Node.BlockNode().Accept(this);
	// StaticFuncMethod.Finish();
	//
	// FuncClass = this.AsmLoader.LoadGeneratedClass(ClassName);
	// this.SetGeneratedClass(ClassName, FuncClass);
	// return new JavaStaticFieldNode(null, FuncClass, FuncType, "function");
	// }

	// @Override public void VisitArrayLiteralNode(ZArrayLiteralNode Node) {
	// if(Node.IsUntyped()) {
	// ZLogger._LogError(Node.SourceToken, "ambigious array");
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// }
	// else {
	// Class<?> ArrayClass = LibAsm.AsArrayClass(Node.Type);
	// String Owner = Type.getInternalName(ArrayClass);
	// this.mBuilder.visitTypeInsn(NEW, Owner);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(Node.Type.TypeId);
	// this.mBuilder.PushNodeListAsArray(LibAsm.AsElementClass(Node.Type), 0,
	// Node);
	// this.mBuilder.SetLineNumber(Node);
	// this.mBuilder.visitMethodInsn(INVOKESPECIAL, Owner, "<init>",
	// LibAsm.NewArrayDescriptor(Node.Type));
	// }
	// }

	// @Override
	// public void VisitMapLiteralNode(ZMapLiteralNode Node) {
	// if (Node.IsUntyped()) {
	// ZLogger._LogError(Node.SourceToken, "ambigious map");
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// } else {
	// String Owner = Type.getInternalName(ZObjectMap.class);
	// this.mBuilder.visitTypeInsn(NEW, Owner);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(Node.Type.TypeId);
	// this.mBuilder.PushInt(Node.GetListSize() * 2);
	// this.mBuilder.visitTypeInsn(ANEWARRAY,
	// Type.getInternalName(Object.class));
	// for (int i = 0; i < Node.GetListSize(); i++) {
	// ZMapEntryNode EntryNode = Node.GetMapEntryNode(i);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(i * 2);
	// this.mBuilder.PushNode(String.class, EntryNode.KeyNode());
	// this.mBuilder.visitInsn(Opcodes.AASTORE);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(i * 2 + 1);
	// this.mBuilder.PushNode(Object.class, EntryNode.ValueNode());
	// this.mBuilder.visitInsn(Opcodes.AASTORE);
	// }
	// this.mBuilder.SetLineNumber(Node);
	// String Desc = Type.getMethodDescriptor(Type.getType(void.class), new
	// Type[] { Type.getType(int.class), Type.getType(Object[].class) });
	// this.mBuilder.visitMethodInsn(INVOKESPECIAL, Owner, "<init>", Desc);
	// }
	// }
	//
	// @Override
	// public void VisitNewObjectNode(ZNewObjectNode Node) {
	// if (Node.IsUntyped()) {
	// ZLogger._LogError(Node.SourceToken, "no class for new operator");
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// } else {
	// String ClassName = Type.getInternalName(this.GetJavaClass(Node.Type));
	// this.mBuilder.visitTypeInsn(NEW, ClassName);
	// this.mBuilder.visitInsn(DUP);
	// Constructor<?> jMethod = this.GetConstructor(Node.Type, Node);
	// if (jMethod != null) {
	// Class<?>[] P = jMethod.getParameterTypes();
	// for (int i = 0; i < P.length; i++) {
	// this.mBuilder.PushNode(P[i], Node.GetListAt(i));
	// }
	// this.mBuilder.SetLineNumber(Node);
	// this.mBuilder.visitMethodInsn(INVOKESPECIAL, ClassName, "<init>",
	// Type.getConstructorDescriptor(jMethod));
	// } else {
	// ZLogger._LogError(Node.SourceToken, "no constructor: " + Node.Type);
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// }
	// }
	// }
	//
	// public void VisitStaticFieldNode(JavaStaticFieldNode Node) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC,
	// Type.getInternalName(Node.StaticClass), Node.FieldName,
	// this.GetJavaClass(Node.Type));
	// }
	//
	// @Override
	// public void VisitGlobalNameNode(ZGlobalNameNode Node) {
	// if (Node.IsFuncNameNode()) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC,
	// this.NameFunctionClass(Node.GlobalName, Node.FuncType), "f",
	// this.GetJavaClass(Node.Type));
	// } else if (!Node.IsUntyped()) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC,
	// this.NameGlobalNameClass(Node.GlobalName), "_",
	// this.GetJavaClass(Node.Type));
	// } else {
	// ZLogger._LogError(Node.SourceToken, "undefined symbol: " +
	// Node.GlobalName);
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// }
	// }
	//
	// @Override
	// public void VisitGetterNode(ZGetterNode Node) {
	// if (Node.IsUntyped()) {
	// Method sMethod = JavaMethodTable.GetStaticMethod("GetField");
	// ZNode NameNode = new ZStringNode(Node, null, Node.GetName());
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), NameNode });
	// } else {
	// Class<?> RecvClass = this.GetJavaClass(Node.RecvNode().Type);
	// Field jField = this.GetField(RecvClass, Node.GetName());
	// String Owner = Type.getType(RecvClass).getInternalName();
	// String Desc = Type.getType(jField.getType()).getDescriptor();
	// if (Modifier.isStatic(jField.getModifiers())) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC, Owner, Node.GetName(),
	// Desc);
	// } else {
	// this.mBuilder.PushNode(null, Node.RecvNode());
	// this.mBuilder.visitFieldInsn(GETFIELD, Owner, Node.GetName(), Desc);
	// }
	// this.mBuilder.CheckReturnCast(Node, jField.getType());
	// }
	// }
	//
	// @Override
	// public void VisitSetterNode(ZSetterNode Node) {
	// if (Node.IsUntyped()) {
	// Method sMethod = JavaMethodTable.GetStaticMethod("SetField");
	// ZNode NameNode = new ZStringNode(Node, null, Node.GetName());
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), NameNode, Node.ExprNode() });
	// } else {
	// Class<?> RecvClass = this.GetJavaClass(Node.RecvNode().Type);
	// Field jField = this.GetField(RecvClass, Node.GetName());
	// String Owner = Type.getType(RecvClass).getInternalName();
	// String Desc = Type.getType(jField.getType()).getDescriptor();
	// if (Modifier.isStatic(jField.getModifiers())) {
	// this.mBuilder.PushNode(jField.getType(), Node.ExprNode());
	// this.mBuilder.visitFieldInsn(PUTSTATIC, Owner, Node.GetName(), Desc);
	// } else {
	// this.mBuilder.PushNode(null, Node.RecvNode());
	// this.mBuilder.PushNode(jField.getType(), Node.ExprNode());
	// this.mBuilder.visitFieldInsn(PUTFIELD, Owner, Node.GetName(), Desc);
	// }
	// }
	// }
	//
	// @Override
	// public void VisitGetIndexNode(ZGetIndexNode Node) {
	// Method sMethod =
	// JavaMethodTable.GetBinaryStaticMethod(Node.RecvNode().Type, "[]",
	// Node.IndexNode().Type);
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), Node.IndexNode() });
	// }
	//
	// @Override
	// public void VisitSetIndexNode(ZSetIndexNode Node) {
	// Method sMethod =
	// JavaMethodTable.GetBinaryStaticMethod(Node.RecvNode().Type, "[]=",
	// Node.IndexNode().Type);
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), Node.IndexNode(), Node.ExprNode() });
	// }
	//
	// @Override
	// public void VisitMethodCallNode(ZMethodCallNode Node) {
	// this.mBuilder.SetLineNumber(Node);
	// Method jMethod = this.GetMethod(Node.RecvNode().Type, Node.MethodName(),
	// Node);
	// if (jMethod != null) {
	// if (!Modifier.isStatic(jMethod.getModifiers())) {
	// this.mBuilder.PushNode(null, Node.RecvNode());
	// }
	// Class<?>[] P = jMethod.getParameterTypes();
	// for (int i = 0; i < P.length; i++) {
	// this.mBuilder.PushNode(P[i], Node.GetListAt(i));
	// }
	// int inst = this.GetInvokeType(jMethod);
	// String owner = Type.getInternalName(jMethod.getDeclaringClass());
	// this.mBuilder.visitMethodInsn(inst, owner, jMethod.getName(),
	// Type.getMethodDescriptor(jMethod));
	// this.mBuilder.CheckReturnCast(Node, jMethod.getReturnType());
	// } else {
	// jMethod = JavaMethodTable.GetStaticMethod("InvokeUnresolvedMethod");
	// this.mBuilder.PushNode(Object.class, Node.RecvNode());
	// this.mBuilder.PushConst(Node.MethodName());
	// this.mBuilder.PushNodeListAsArray(Object.class, 0, Node);
	// this.mBuilder.ApplyStaticMethod(Node, jMethod);
	// }
	// }
	//
	// @Override
	// public void VisitInstanceOfNode(ZInstanceOfNode Node) {
	// if (!(Node.LeftNode().Type instanceof ZGenericType)) {
	// this.VisitNativeInstanceOfNode(Node);
	// return;
	// }
	// Node.LeftNode().Accept(this);
	// this.mBuilder.Pop(Node.LeftNode().Type);
	// this.mBuilder.PushLong(Node.LeftNode().Type.TypeId);
	// this.mBuilder.PushLong(Node.TargetType().TypeId);
	// Method method = JavaMethodTable.GetBinaryStaticMethod(ZType.IntType,
	// "==", ZType.IntType);
	// String owner = Type.getInternalName(method.getDeclaringClass());
	// this.mBuilder.visitMethodInsn(INVOKESTATIC, owner, method.getName(),
	// Type.getMethodDescriptor(method));
	// }
	//
	// private void VisitNativeInstanceOfNode(ZInstanceOfNode Node) {
	// Class<?> JavaClass = this.GetJavaClass(Node.TargetType());
	// if (Node.TargetType().IsIntType()) {
	// JavaClass = Long.class;
	// } else if (Node.TargetType().IsFloatType()) {
	// JavaClass = Double.class;
	// } else if (Node.TargetType().IsBooleanType()) {
	// JavaClass = Boolean.class;
	// }
	// this.invokeBoxingMethod(Node.LeftNode());
	// this.mBuilder.visitTypeInsn(INSTANCEOF, JavaClass);
	// }
	//
	// private void invokeBoxingMethod(ZNode TargetNode) {
	// Class<?> TargetClass = Object.class;
	// if (TargetNode.Type.IsIntType()) {
	// TargetClass = Long.class;
	// } else if (TargetNode.Type.IsFloatType()) {
	// TargetClass = Double.class;
	// } else if (TargetNode.Type.IsBooleanType()) {
	// TargetClass = Boolean.class;
	// }
	// Class<?> SourceClass = this.GetJavaClass(TargetNode.Type);
	// Method Method = JavaMethodTable.GetCastMethod(TargetClass, SourceClass);
	// TargetNode.Accept(this);
	// if (!TargetClass.equals(Object.class)) {
	// String owner = Type.getInternalName(Method.getDeclaringClass());
	// this.mBuilder.visitMethodInsn(INVOKESTATIC, owner, Method.getName(),
	// Type.getMethodDescriptor(Method));
	// }
	// }
	//
	// @Override
	// public void VisitAndNode(ZAndNode Node) {
	// Label elseLabel = new Label();
	// Label mergeLabel = new Label();
	// this.mBuilder.PushNode(boolean.class, Node.LeftNode());
	// this.mBuilder.visitJumpInsn(IFEQ, elseLabel);
	//
	// this.mBuilder.PushNode(boolean.class, Node.RightNode());
	// this.mBuilder.visitJumpInsn(IFEQ, elseLabel);
	//
	// this.mBuilder.visitLdcInsn(true);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(elseLabel);
	// this.mBuilder.visitLdcInsn(false);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(mergeLabel);
	// }
	//
	// @Override
	// public void VisitOrNode(ZOrNode Node) {
	// Label thenLabel = new Label();
	// Label mergeLabel = new Label();
	// this.mBuilder.PushNode(boolean.class, Node.LeftNode());
	// this.mBuilder.visitJumpInsn(IFNE, thenLabel);
	//
	// this.mBuilder.PushNode(boolean.class, Node.RightNode());
	// this.mBuilder.visitJumpInsn(IFNE, thenLabel);
	//
	// this.mBuilder.visitLdcInsn(false);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(thenLabel);
	// this.mBuilder.visitLdcInsn(true);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(mergeLabel);
	// }

}