package nez.lang;

import nez.ast.SourcePosition;
import nez.runtime.Instruction;
import nez.runtime.NezCompiler;

public class Capture extends ASTOperation {
	public int shift;
	Capture(SourcePosition s, int shift) {
		super(s);
		this.shift = shift;
	}
	@Override
	public String getPredicate() { 
		return "}";
	}
	@Override
	public String key() {
		return shift == 0 ? "}" : "}["+shift+"]";
	}
	@Override
	public Expression reshape(GrammarReshaper m) {
		return m.reshapeCapture(this);
	}

	@Override
	public boolean isConsumed(Stacker stacker) {
		return false;
	}
	
	@Override
	public Instruction encode(NezCompiler bc, Instruction next) {
		return bc.encodeCapture(this, next);
	}
}
