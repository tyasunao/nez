package nez.tool.peg;

import nez.lang.Expression;
import nez.lang.Grammar;
import nez.lang.Nez;
import nez.lang.NonTerminal;
import nez.lang.Production;

public class PEGTLTranslator extends GrammarTranslator {

	@Override
	public String getFileExtension() {
		return "hpp";
	}

	@Override
	public void makeHeader(Grammar gg) {
		L("// The following is generated by the Nez Grammar Generator ");
		L("#include<pegtl.hh>");
		for (Production p : gg) {
			L("struct " + name(p) + ";");
		}

	}

	@Override
	public void makeFooter(Grammar gg) {

	}

	@Override
	protected String name(Production p) {
		return "p" + p.getLocalName().replace("~", "_").replace("!", "NOT").replace(".", "DOT");
	}

	protected String _Open() {
		return "<";
	};

	protected String _Close() {
		return ">";
	};

	protected String _Delim() {
		return ",";
	};

	public void visitGrouping(Expression e) {
		// W(_OpenGrouping());
		visitExpression(e);
		// W(_CloseGrouping());
	}

	@Override
	public void visitProduction(Grammar gg, Production p) {
		Expression e = p.getExpression();
		L("struct " + name(p) + " : ");
		Begin("");
		W("pegtl::seq<");
		visitExpression(e);
		W(", pegtl::success> {};");
		End("");
	}

	@Override
	public void visitEmpty(Expression e) {
		C("pegtl::success");
	}

	@Override
	public void visitFail(Expression e) {
		C("pegtl::failure");
	}

	@Override
	public void visitNonTerminal(NonTerminal e) {
		W(name(e.getProduction()));
	}

	@Override
	public void visitByte(Nez.Byte e) {
		C("pegtl::one", e.byteChar);
	}

	@Override
	public void visitByteSet(Nez.ByteSet e) {
		C("pegtl::one", e.byteMap);
	}

	public void visitString(String s) {
		int cnt = 0;
		W("pegtl::string");
		W(_Open());
		for (int c = 0; c < s.length(); c++) {
			if (cnt > 0) {
				W(_Delim());
			}
			W(String.valueOf((int) s.charAt(c)));
			cnt++;
		}
		W(_Close());
	}

	@Override
	public void visitAny(Nez.Any e) {
		W("pegtl::any");
	}

	@Override
	public void visitOption(Nez.Option e) {
		C("pegtl::opt", e);
	}

	@Override
	public void visitZeroMore(Nez.ZeroMore e) {
		C("pegtl::star", e);
	}

	@Override
	public void visitOneMore(Nez.OneMore e) {
		C("pegtl::plus", e);
	}

	@Override
	public void visitAnd(Nez.And e) {
		C("pegtl::at", e);
	}

	@Override
	public void visitNot(Nez.Not e) {
		C("pegtl::not_at", e);
	}

	@Override
	public void visitChoice(Nez.Choice e) {
		C("pegtl::sor", e);
	}

	@Override
	public void visitPair(Nez.Pair e) {
		W("pegtl::seq<");
		// super.visitPair(e);
		W(">");
	}

	@Override
	public void visitPreNew(Nez.BeginTree e) {
		W("pegtl::success");
		// if(e.lefted) {
		// C("LCapture", e.shift);
		// }
		// else {
		// C("NCapture", e.shift);
		// }
	}

	@Override
	public void visitNew(Nez.EndTree e) {
		W("pegtl::success");
		// C("Capture", e.shift);
	}

	@Override
	public void visitTag(Nez.Tag e) {
		W("pegtl::success");
		// C("Tagging", e.getTagName());
	}

	@Override
	public void visitReplace(Nez.Replace e) {
		W("pegtl::success");
		// C("Replace", StringUtils.quoteString('"', e.value, '"'));
	}

	@Override
	public void visitLink(Nez.Link e) {
		// if(e.index != -1) {
		// C("Link", String.valueOf(e.index), e);
		// }
		// else {
		// C("Link", e);
		// }
		visitExpression(e.get(0));
	}

	@Override
	public void visitUndefined(Expression e) {
		if (e.size() > 0) {
			visitExpression(e.get(0));
		} else {
			W("pegtl::success");
		}
		// W("<");
		// W(e.getPredicate());
		// for(Expression se : e) {
		// W(" ");
		// visit(se);
		// }
		// W(">");
	}

	@Override
	public void visitString(Nez.MultiByte p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitBlockScope(Nez.BlockScope p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitLocalScope(Nez.LocalScope p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitSymbolAction(Nez.SymbolAction p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitSymbolExists(Nez.SymbolExists p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitSymbolMatch(Nez.SymbolMatch p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitSymbolPredicate(Nez.SymbolPredicate p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitDetree(Nez.Detree p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitIf(Nez.If p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitOn(Nez.On p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitLeftFold(Nez.LeftFold p) {
		// TODO Auto-generated method stub

	}

}