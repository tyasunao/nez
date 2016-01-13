package nez.bx;

import java.util.HashMap;

import nez.ast.Symbol;
import nez.ast.TreeVisitorMap;
import nez.bx.FormatGenerator.DefaultVisitor;
import nez.lang.Expression;
import nez.lang.Nez;
import nez.lang.Nez.Pair;
import nez.lang.NonTerminal;
import nez.lang.Production;
import nez.parser.moz.ParserGrammar;
import nez.util.FileBuilder;

public class FormatGenerator extends TreeVisitorMap<DefaultVisitor> {
	private String dir = "./devel/nez/bx";
	private String outputFile = "GeneratedFormat";
	private FileBuilder file;
	private ParserGrammar grammar = null;

	private HashMap<String, Captured> nonterminalMap = new HashMap<String, Captured>();
	private Captured[] capturedList = new Captured[4];
	private Elements[] elementsStack = new Elements[4];
	private String currentRuleName = null;
	private int capturedId = 0;
	private int stackTop = 0;
	private Elements checkedNonterminal = new Elements();

	public FormatGenerator(String dir, String outputFile) {
		if (outputFile != null) {
			this.outputFile = outputFile;
		}
		if (dir != null) {
			this.dir = dir;
		}
		init(FormatGenerator.class, new DefaultVisitor());
	}

	public void generate(ParserGrammar grammar) {
		this.grammar = grammar;
		this.openOutputFile();
		this.makeFormat();
		this.setFormat();
		this.writeFormat();
	}

	public void openOutputFile() {
		if (outputFile == null) {
			this.file = new FileBuilder(null);
		} else {
			String path = dir + "/" + outputFile + ".txt";
			this.file = new FileBuilder(path);
			System.out.println("generating " + path + " ... ");
		}
	}

	public void makeFormat() {
		for (Production rule : grammar) {
			elementsStack[stackTop] = new Elements();
			currentRuleName = rule.getLocalName();
			makeProductionFormat(rule);
			nonterminalMap.put(currentRuleName, new Captured(elementsStack[stackTop], currentRuleName));
		}
	}

	public void setFormat() {
		for (int i = 0; i < this.capturedList.length; i++) {
			if (this.capturedList[i] == null) {
				break;
			}
			capturedList[i].setFormat(i);
		}
	}

	public void writeFormat() {
		for (int i = 0; i < this.capturedList.length; i++) {
			if (this.capturedList[i] == null) {
				break;
			}
			capturedList[i].writeFormat(i);
		}
	}

	public void makeProductionFormat(Production rule) {
		visit(rule.getExpression());
	}

	public class DefaultVisitor {
		public void accept(Expression e) {
		}
	}

	public void visit(Expression e) {
		find(e.getClass().getSimpleName()).accept(e);
	}

	public class _NonTerminal extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			addElement(new NonTerminalElement(((NonTerminal) e).getLocalName()));
		}
	}

	public class _Choice extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			Elements[] branch = new Elements[e.size()];
			if (stackTop + 1 == elementsStack.length) {
				Elements[] newList = new Elements[elementsStack.length * 2];
				System.arraycopy(elementsStack, 0, newList, 0, elementsStack.length);
				elementsStack = newList;
			}
			for (int i = 0; i < e.size(); i++) {
				elementsStack[++stackTop] = new Elements();
				visit(e.get(i));
				branch[i] = elementsStack[stackTop--];
			}
			addElement(new ChoiceElement(branch));
		}
	}

	public class _Pair extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			visit(((Pair) e).first);
			visit(((Pair) e).next);
		}
	}

	public class _Byte extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			addElement(new ByteElement(((Nez.Byte) e).byteChar));
		}
	}

	public class _EndTree extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			if (capturedId == capturedList.length) {
				Captured[] newList = new Captured[capturedList.length * 2];
				System.arraycopy(capturedList, 0, newList, 0, capturedList.length);
				capturedList = newList;
			}
			capturedList[capturedId] = new Captured(elementsStack[stackTop--]);
			if (elementsStack[stackTop].hasLF && !elementsStack[stackTop].inOptional) {
				Elements[] branch = { new Elements(), null };
				branch[0].addElement(new CapturedElement(capturedId));
				branch[1] = ((LinkedElement) capturedList[capturedId].elements.get(0)).inner;
				((LinkedElement) capturedList[capturedId].elements.get(0)).inner = new Elements(new ChoiceElement(branch));
			}
			addElement(new CapturedElement(capturedId++));
		}
	}

	public class _FoldTree extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			elementsStack[stackTop].hasLF = true;
			if (stackTop + 1 == elementsStack.length) {
				Elements[] newList = new Elements[elementsStack.length * 2];
				System.arraycopy(elementsStack, 0, newList, 0, elementsStack.length);
				elementsStack = newList;
			}
			elementsStack[++stackTop] = new Elements();
			addElement(new LinkedElement(((Nez.FoldTree) e).label, new Elements(getLeft(stackTop - 2))));
		}
	}

	public Element getLeft(int stackTop) {
		int size = elementsStack[stackTop].size;
		if (size != 0) {
			elementsStack[stackTop].size--;
			return elementsStack[stackTop].elementList[size - 1];
		}
		return getLeft(stackTop - 1);
	}

	public class _BeginTree extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			if (stackTop + 1 == elementsStack.length) {
				Elements[] newList = new Elements[elementsStack.length * 2];
				System.arraycopy(elementsStack, 0, newList, 0, elementsStack.length);
				elementsStack = newList;
			}
			elementsStack[++stackTop] = new Elements();
		}
	}

	public class _Tag extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			addElement(new TagElement(((Nez.Tag) e).getTagName()));
		}
	}

	public class _Option extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			if (stackTop + 1 == elementsStack.length) {
				Elements[] newList = new Elements[elementsStack.length * 2];
				System.arraycopy(elementsStack, 0, newList, 0, elementsStack.length);
				elementsStack = newList;
			}
			elementsStack[++stackTop] = new Elements();
			elementsStack[stackTop].inOptional = true;
			visit(e.get(0));
			stackTop--;
			if (elementsStack[stackTop + 1].hasLF) {
				Elements[] branch = { null, new Elements() };
				branch[0] = elementsStack[stackTop + 1];
				branch[1].addElement(elementsStack[stackTop].get(elementsStack[stackTop].size));
				addElement(new ChoiceElement(branch));
			} else {
				addElement(new OptionElement(elementsStack[stackTop + 1]));
			}
		}
	}

	public class _ZeroMore extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			if (stackTop + 1 == elementsStack.length) {
				Elements[] newList = new Elements[elementsStack.length * 2];
				System.arraycopy(elementsStack, 0, newList, 0, elementsStack.length);
				elementsStack = newList;
			}
			elementsStack[++stackTop] = new Elements();
			visit(e.get(0));
			stackTop--;
			if (elementsStack[stackTop + 1].hasLF) {
				Elements[] branch = { null, new Elements() };
				branch[0] = elementsStack[stackTop + 1];
				branch[1].addElement(elementsStack[stackTop].get(elementsStack[stackTop].size));
				addElement(new ChoiceElement(branch));
			} else {
				addElement(new ZeroElement(elementsStack[stackTop + 1]));
			}
		}
	}

	public class _OneMore extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			if (stackTop + 1 == elementsStack.length) {
				Elements[] newList = new Elements[elementsStack.length * 2];
				System.arraycopy(elementsStack, 0, newList, 0, elementsStack.length);
				elementsStack = newList;
			}
			elementsStack[++stackTop] = new Elements();
			visit(e.get(0));
			stackTop--;
			if (elementsStack[stackTop + 1].hasLF) {
				addElement(elementsStack[stackTop + 1].get(0));
			} else {
				addElement(new OneElement(elementsStack[stackTop + 1]));
			}
		}
	}

	public class _LinkTree extends DefaultVisitor {
		@Override
		public void accept(Expression e) {
			if (stackTop + 1 == elementsStack.length) {
				Elements[] newList = new Elements[elementsStack.length * 2];
				System.arraycopy(elementsStack, 0, newList, 0, elementsStack.length);
				elementsStack = newList;
			}
			elementsStack[++stackTop] = new Elements();
			visit(e.get(0));
			stackTop--;
			addElement(new LinkedElement(((Nez.LinkTree) e).label, elementsStack[stackTop + 1]));
		}
	}

	public void addElement(Element element) {
		elementsStack[stackTop].addElement(element);
	}

	class Captured {
		String name;
		Elements elements;
		FormatSet[] formatSet = new FormatSet[4];
		int size = 0;

		public Captured(Elements elements) {
			name = null;
			this.elements = elements;
		}

		public Captured(Elements elements, String name) {
			this(elements);
			this.name = name;
		}

		public void setFormat(int capturedId) {
			while (true) {
				String tag = searchTag();
				if (tag != null) {
					if (size == formatSet.length) {
						FormatSet[] newList = new FormatSet[formatSet.length * 2];
						System.arraycopy(formatSet, 0, newList, 0, formatSet.length);
						formatSet = newList;
					}
					formatSet[size] = new FormatSet();
					formatSet[size].tag = tag;
					formatSet[size++].link = searchLink();
				} else {
					break;
				}
			}
		}

		public void writeFormat(int capturedId) {
			for (int i = 0; i < size; i++) {
				for (int labelProgression = 0, tagProgression = 0; true; tagProgression++) {
					LabelSet labelSet = new LabelSet(labelProgression, tagProgression);
					String label = optionFix(formatSet[i].link, labelSet);
					if (labelSet.labelProgression > 0) {
						break;
					}
					if (labelSet.tagProgression > 0) {
						labelProgression++;
						tagProgression = -1;
						continue;
					}
					writeln("format " + formatSet[i].tag);
					if (label == null) {
						label = "";
					}
					write("(" + label + ")`");
					String format = toFormat();
					if (format == null) {
						writeln("${this.toText()}");
					} else {
						writeln(format);
					}
					writeln("`");
					writeln("");
				}
			}
		}

		public String optionFix(Elements links, LabelSet labelSet) {
			if (links == null) {
				return null;
			}
			for (int i = 0; i < links.size; i++) {
				Element link = links.get(i);
				labelSet = link.optionFix(labelSet);
			}
			return labelSet.label;
		}

		public String searchTag() {
			return elements.searchTag();
		}

		public Elements searchLink() {
			return elements.searchLink();
		}

		public LinkedInner[] checkInner() {
			return elements.checkInner();
		}

		public String toFormat() {
			String formats = elements.toFormat();
			if (this.name == null) {
				if (formats == null || formats.indexOf('$') == -1) {
					return null;
				}
			}
			return formats;
		}

		@Override
		public String toString() {
			return this.elements.toString();
		}
	}

	class Elements {
		Element[] elementList;
		int size;
		boolean hasLF = false;
		boolean inOptional = false;

		public Elements() {
			elementList = new Element[4];
			size = 0;
		}

		public Elements(Element element) {
			elementList = new Element[4];
			elementList[0] = element;
			size = 1;
		}

		public Element get(int i) {
			return elementList[i];
		}

		public String searchTag() {
			for (int i = 0; i < this.size; i++) {
				String tag = elementList[i].searchTag();
				if (tag != null) {
					return tag;
				}
			}
			return null;
		}

		public Elements searchLink() {
			Elements links = null;
			for (int i = 0; i < this.size; i++) {
				Elements link = elementList[i].searchLink();
				if (link != null) {
					if (links == null) {
						links = link;
					} else {
						links.addElements(link);
					}
				}
			}
			return links;
		}

		public LinkedInner[] checkInner() {
			LinkedInner[] inners = null;
			for (int i = 0; i < size; i++) {
				LinkedInner[] inner = elementList[i].checkInner();
				if (inner != null) {
					if (inners == null) {
						inners = inner;
					} else {
						LinkedInner[] newList = new LinkedInner[inner.length * inners.length];
						for (int j = 0; j < inners.length; j++) {
							for (int k = 0; k < inner.length; k++) {
								newList[j * inner.length + k] = new LinkedInner();
								newList[j * inner.length + k].join(inners[j]);
								newList[j * inner.length + k].join(inner[k]);
							}
						}
						inners = newList;
					}
				}
			}
			return inners;
		}

		public String toFormat() {
			String formats = null;
			for (int i = 0; i < this.size; i++) {
				Element element = elementList[i];
				String format = element.toFormat();
				if (format != null) {
					if (formats == null) {
						formats = format;
					} else {
						formats += " " + format;
					}
				}
			}
			return formats;
		}

		public boolean has(Element element) {
			for (int i = 0; i < size; i++) {
				if (elementList[i] == element) {
					return true;
				}
			}
			return false;
		}

		public void addElement(Element element) {
			if (size == elementList.length) {
				Element[] newList = new Element[elementList.length * 2];
				System.arraycopy(elementList, 0, newList, 0, elementList.length);
				elementList = newList;
			}
			elementList[size++] = element;
		}

		public void addElements(Elements elements) {
			for (int i = 0; i < elements.size; i++) {
				addElement(elements.elementList[i]);
			}
		}

		@Override
		public String toString() {
			if (elementList[0] == null) {
				return " ";
			}
			String text = elementList[0].toString();
			for (int i = 1; i < elementList.length; i++) {
				if (elementList[i] == null) {
					break;
				}
				text += " ";
				text += elementList[i].toString();
			}
			return text;
		}
	}

	abstract class Element {
		public String searchTag() {
			return null;
		}

		public Elements searchLink() {
			return null;
		}

		public LinkedInner[] checkInner() {
			return null;
		}

		public LabelSet optionFix(LabelSet labelSet) {
			assert true;
			return null;
		}

		public int countOption() {
			return 1;
		}

		public int[] checkNeedTag() {
			return null;
		}

		public String toFormat() {
			return null;
		}

		public boolean hasUnclarity() {
			return false;
		}

		@Override
		abstract public String toString();
	}

	class NonTerminalElement extends Element {
		String name;
		Captured captured;

		public NonTerminalElement(String name) {
			this.name = name;
		}

		@Override
		public Elements searchLink() {
			nullCheck();
			return captured.searchLink();
		}

		@Override
		public String searchTag() {
			nullCheck();
			return captured.searchTag();
		}

		@Override
		public LinkedInner[] checkInner() {
			nullCheck();
			if (checkedNonterminal.has(this)) {
				return null;
			}
			checkedNonterminal.addElement(this);
			return captured.checkInner();
		}

		@Override
		public String toFormat() {
			nullCheck();
			return captured.toFormat();
		}

		@Override
		public String toString() {
			return "[" + this.name + "]";
		}

		public void nullCheck() {
			if (captured == null) {
				this.captured = nonterminalMap.get(name);
			}
		}
	}

	class CapturedElement extends Element {
		int id;

		public CapturedElement(int id) {
			this.id = id;
		}

		@Override
		public LinkedInner[] checkInner() {
			LinkedInner[] ret = { new LinkedInner() };
			ret[0].id = this.id;
			return ret;
		}

		@Override
		public String toString() {
			return "[" + this.id + "]";
		}
	}

	class LinkedElement extends Element {
		Symbol label;
		Elements inner;
		LinkedInner[] linkedInner;
		int size;
		int labelFix;

		public LinkedElement(Symbol label, Elements inner) {
			this.label = label;
			this.inner = inner;
		}

		@Override
		public Elements searchLink() {
			linkedInner = inner.checkInner();
			checkedNonterminal = new Elements();
			size = linkedInner.length;
			optimizeLinkedInner();
			return new Elements(this);
		}

		public void optimizeLinkedInner() {
			boolean[] checked = new boolean[capturedId];
			LinkedInner[] newLinkedinner = new LinkedInner[size];
			int newSize = 0;
			for (int i = 0; i < size; i++) {
				if (linkedInner[i].id != 1 && !checked[linkedInner[i].id]) {
					checked[linkedInner[i].id] = true;
					newLinkedinner[newSize] = linkedInner[i];
					newSize++;
				}
			}
			linkedInner = new LinkedInner[newSize];
			System.arraycopy(newLinkedinner, 0, linkedInner, 0, newSize);
			size = newSize;
		}

		@Override
		public LabelSet optionFix(LabelSet labelSet) {
			labelFix = labelSet.tagProgression % size;
			labelSet.tagProgression = labelSet.tagProgression / size;
			String ret = "";
			FormatSet[] formatSet = capturedList[linkedInner[labelFix].id].formatSet;
			ret += toLabel(labelSet.inRepetition) + ": " + formatSet[0].tag;
			for (int j = 1; j < capturedList[linkedInner[labelFix].id].size; j++) {
				ret += " / " + formatSet[j].tag;
			}
			if (labelSet.label == null) {
				labelSet.label = ret;
			} else {
				labelSet.label += ", " + ret;
			}
			return labelSet;
		}

		public String toLabel(boolean inRepetition) {
			String ret;
			if (label == null) {
				ret = "unlabeld";
			} else {
				ret = label.toString();
			}
			if (inRepetition) {
				ret += "[]";
			}
			return ret;
		}

		@Override
		public String toFormat() {
			String ret = "";

			if (label == null) {
				if (!linkedInner[labelFix].before.equals("")) {
					ret += linkedInner[labelFix].before;
				}
				ret += "${this.get(i)}";
				if (!linkedInner[labelFix].after.equals("")) {
					ret += linkedInner[labelFix].after;
				}
			} else {
				if (!linkedInner[labelFix].before.equals("")) {
					ret += linkedInner[labelFix].before;
				}
				ret += "${" + label + "}";
				if (!linkedInner[labelFix].after.equals("")) {
					ret += linkedInner[labelFix].after;
				}
			}

			return ret;
		}

		@Override
		public String toString() {
			return "$" + this.label + this.inner;
		}
	}

	class ByteElement extends Element {
		char cByte;

		public ByteElement(char cByte) {
			this.cByte = cByte;
		}

		public ByteElement(int byteChar) {
			cByte = (char) byteChar;
		}

		@Override
		public LinkedInner[] checkInner() {
			LinkedInner[] ret = { new LinkedInner() };
			ret[0].before = String.valueOf(cByte);
			return ret;
		}

		@Override
		public String toFormat() {
			if (cByte == '\\' || cByte == '\"' || cByte == '\'') {
				return "\\" + cByte;
			} else if (cByte == '\b') {
				return "\\b";
			} else if (cByte == '\t') {
				return "\\t";
			} else if (cByte == '\n') {
				return "\\n";
			} else if (cByte == '\f') {
				return "\\f";
			} else if (cByte == '\r') {
				return "\\r";
			} else {
				return String.valueOf(cByte);
			}
		}

		@Override
		public String toString() {
			if (cByte == '\b') {
				return "\\b";
			} else if (cByte == '\t') {
				return "\\t";
			} else if (cByte == '\n') {
				return "\\n";
			} else if (cByte == '\f') {
				return "\\f";
			} else if (cByte == '\r') {
				return "\\r";
			} else {
				return String.valueOf(cByte);
			}
		}
	}

	class TagElement extends Element {
		String name;
		boolean unused = true;

		public TagElement(String name) {
			this.name = "#" + name;
		}

		@Override
		public String searchTag() {
			if (unused) {
				unused = false;
				return name;
			}
			return null;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	class ChoiceElement extends Element {
		Elements[] branch;
		Elements[] link;
		int rate;
		int[] linkRate;
		int tagFixBranch = -1;
		int linkFixBranch = -1;
		boolean hasNullBranch = false;

		public ChoiceElement(Elements[] branch) {
			this.branch = branch;
		}

		@Override
		public String searchTag() {
			for (int i = 0; i < branch.length; i++) {
				String tag = branch[i].searchTag();
				if (tag != null) {
					tagFixBranch = i;
					return tag;
				}
			}
			tagFixBranch = -1;
			return null;
		}

		@Override
		public Elements searchLink() {
			if (tagFixBranch == -1) {
				for (int i = 0; i < branch.length; i++) {
					Elements choicedLink = branch[i].searchLink();
					if (choicedLink != null) {
						if (link == null) {
							link = new Elements[branch.length];
						}
						link[i] = choicedLink;
					}
				}
				if (link == null) {
					return null;
				}
				return new Elements(this);
			}
			return branch[tagFixBranch].searchLink();
		}

		@Override
		public LinkedInner[] checkInner() {
			LinkedInner[] inners = null;
			for (int i = 0; i < branch.length; i++) {
				LinkedInner[] inner = branch[i].checkInner();
				if (inner != null) {
					if (inners == null) {
						inners = inner;
					} else {
						LinkedInner[] newList = new LinkedInner[inner.length + inners.length];
						System.arraycopy(inners, 0, newList, 0, inners.length);
						System.arraycopy(inner, 0, newList, inners.length, inner.length);
						inners = newList;
					}
				}
			}
			return inners;
		}

		@Override
		public LabelSet optionFix(LabelSet labelSet) {
			if (linkRate == null) {
				countOption();
			}
			linkFixBranch = labelSet.labelProgression % rate;
			int generalProgression = labelSet.labelProgression / rate;
			for (int i = 0; i <= linkRate.length; i++) {
				if (i == linkRate.length) {
					linkFixBranch = -1;
					break;
				}
				if (linkFixBranch < linkRate[i]) {
					labelSet.labelProgression = linkFixBranch;
					for (int j = 0; j < link[i].size; j++) {
						labelSet = link[i].get(j).optionFix(labelSet);
					}
					break;
				} else {
					linkFixBranch -= linkRate[i];
				}
			}
			labelSet.labelProgression = generalProgression;
			return labelSet;
		}

		@Override
		public int countOption() {
			rate = 0;
			linkRate = new int[link.length];
			for (int i = 0; i < linkRate.length; i++) {
				if (link[i] != null) {
					linkRate[i] = 1;
					for (int j = 0; j < link[i].size; j++) {
						linkRate[i] *= link[i].get(j).countOption();
					}
				} else {
					hasNullBranch = true;
				}
				rate += linkRate[i];
			}
			if (hasNullBranch) {
				rate++;
			}
			return rate;
		}

		@Override
		public String toFormat() {
			if (tagFixBranch == -1) {
				if (linkFixBranch == -1) {
					return null;
				} else {
					return branch[linkFixBranch].toFormat();
				}
			} else {
				return branch[tagFixBranch].toFormat();
			}
		}

		@Override
		public String toString() {
			String text = "( " + branch[0];
			for (int i = 1; i < branch.length; i++) {
				text += " / " + branch[i].toString();
			}
			return text + " )";
		}
	}

	class OneElement extends Element {
		Elements inner;
		Elements link;
		int rate;

		public OneElement(Elements inner) {
			this.inner = inner;
		}

		@Override
		public String searchTag() {
			String tag = inner.searchTag();
			if (tag != null) {
				return tag;
			}
			return null;
		}

		@Override
		public Elements searchLink() {
			Elements oneLink = inner.searchLink();
			if (oneLink == null) {
				return null;
			}
			link = oneLink;
			return new Elements(this);
		}

		@Override
		public LabelSet optionFix(LabelSet labelSet) {
			if (rate == 0) {
				countOption();
			}
			int generalProgression = labelSet.labelProgression / rate;
			labelSet.labelProgression = labelSet.labelProgression % rate;
			labelSet.inRepetition = true;
			for (int j = 0; j < link.size; j++) {
				labelSet = link.get(j).optionFix(labelSet);
			}
			labelSet.labelProgression = generalProgression;
			labelSet.inRepetition = false;
			return labelSet;
		}

		@Override
		public int countOption() {
			rate = 1;
			if (link != null) {
				for (int j = 0; j < link.size; j++) {
					rate *= link.get(j).countOption();
				}
			}
			return rate;
		}

		@Override
		public String toFormat() {
			if (inner.toFormat() != null) {
				return "\nwhile(true){\n" + inner.toFormat() + "\n}\n";
			}
			return null;
		}

		@Override
		public String toString() {
			return "(" + this.inner + ")+";
		}
	}

	class ZeroElement extends Element {
		Elements inner;
		Elements link;
		boolean tagFix = false;
		int rate;
		boolean linkFix = false;

		public ZeroElement(Elements inner) {
			this.inner = inner;
		}

		@Override
		public String searchTag() {
			String tag = inner.searchTag();
			if (tag != null) {
				tagFix = true;
				return tag;
			}
			tagFix = false;
			return null;
		}

		@Override
		public Elements searchLink() {
			Elements zeroLink = inner.searchLink();
			if (zeroLink == null) {
				return null;
			}
			link = zeroLink;
			return new Elements(this);
		}

		@Override
		public LabelSet optionFix(LabelSet labelSet) {
			if (rate == 0) {
				countOption();
			}
			int generalProgression = labelSet.labelProgression / rate;
			labelSet.labelProgression = labelSet.labelProgression % rate;
			labelSet.inRepetition = true;
			if (labelSet.labelProgression != rate - 1 && !tagFix) {
				for (int j = 0; j < link.size; j++) {
					labelSet = link.get(j).optionFix(labelSet);
				}
				linkFix = true;
			} else {
				linkFix = false;
			}
			labelSet.labelProgression = generalProgression;
			labelSet.inRepetition = false;
			return labelSet;
		}

		@Override
		public int countOption() {
			rate = 1;
			if (link != null) {
				for (int j = 0; j < link.size; j++) {
					rate *= link.get(j).countOption();
				}
			}
			if (!tagFix) {
				rate++;
			}
			return rate;
		}

		@Override
		public String toFormat() {
			if (!tagFix && !linkFix) {
				return null;
			}
			return "\nwhile(true){\n" + inner.toFormat() + "\n}\n";
		}

		@Override
		public String toString() {
			return "(" + this.inner + ")*";
		}
	}

	class OptionElement extends Element {
		Elements inner;
		Elements link;
		boolean tagFix = false;
		int rate;
		boolean linkFix = false;

		public OptionElement(Elements inner) {
			this.inner = inner;
		}

		@Override
		public String searchTag() {
			String tag = inner.searchTag();
			if (tag != null) {
				tagFix = true;
				return tag;
			}
			tagFix = false;
			return null;
		}

		@Override
		public Elements searchLink() {
			Elements optionalLink = inner.searchLink();
			if (tagFix) {
				return optionalLink;
			}
			if (optionalLink == null) {
				return null;
			}
			link = optionalLink;
			return new Elements(this);
		}

		@Override
		public LabelSet optionFix(LabelSet labelSet) {
			if (rate == 0) {
				countOption();
			}
			int currentBranch = labelSet.labelProgression % rate;
			int generalProgression = labelSet.labelProgression / rate;
			if (currentBranch != rate - 1) {
				labelSet.labelProgression = currentBranch;
				for (int j = 0; j < link.size; j++) {
					labelSet = link.get(j).optionFix(labelSet);
				}
				linkFix = true;
			} else {
				linkFix = false;
			}
			labelSet.labelProgression = generalProgression;
			return labelSet;
		}

		@Override
		public int countOption() {
			rate = 1;
			if (link != null) {
				for (int j = 0; j < link.size; j++) {
					rate *= link.get(j).countOption();
				}
			}
			return ++rate;
		}

		@Override
		public String toFormat() {
			if (!tagFix) {
				if (!linkFix) {
					return null;
				}
				return inner.toFormat();
			}
			return inner.toFormat();
		}

		@Override
		public String toString() {
			return "(" + this.inner + ")?";
		}

	}

	class LabelSet {
		String label;
		int labelProgression;
		int tagProgression;
		boolean inRepetition;

		public LabelSet(int lagelProgression, int tagProgression) {
			this.labelProgression = lagelProgression;
			this.tagProgression = tagProgression;
			this.inRepetition = false;
		}
	}

	class LinkedInner {
		int id = -1;
		String before = "";
		String after = "";

		public void join(LinkedInner target) {
			if (target.id != -1) {
				assert (id == -1);
				this.id = target.id;
				this.before += target.before;
				this.after = target.after;
			} else {
				if (this.id == -1) {
					this.before += target.before;
				} else {
					this.after += target.before;
				}
			}
		}

		@Override
		public String toString() {
			return before + "{" + id + "}" + after;
		}
	}

	class FormatSet {
		String tag;
		Elements link;
	}

	public void writeln(String line) {
		file.writeIndent(line);
	}

	public void write(String word) {
		file.write(word);
	}
}
