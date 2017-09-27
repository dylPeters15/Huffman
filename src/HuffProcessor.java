import java.util.HashMap;
import java.util.PriorityQueue;

public class HuffProcessor implements Processor {

	/*
	 * Compresses the file from BitInputStream in and writes it to the file 
	 * specified by BitOutputStream out by creating a frequency-dependent Huffman tree
	 * @param BitInputStream in: The uncompressed file from which to read
	 * @param BitInputStream out: The file to write the compressed information to
	 * Output: void
	 */
	@Override
	public void compress(BitInputStream in, BitOutputStream out) {

		//count the frequency of each character in the file
		int[] freqs = countFreqs(in);
		//sort the characters by frequency
		PriorityQueue<HuffNode> PQ = populatePQ(freqs);
		//use the sorted characters to create a Hufftree
		HuffNode root = makeTree(PQ);

		//Write the header and body of compressed file. 
		//The writeHeader method writes the HUFF_NUMBER at the beginning, 
		//and the writeBody method writes the PSEUDO_EOF at the end
		writeHeader(root,out);
		writeBody(root,in,out);

	}

	/*
	 * Count the frequency with which each character in the input file occurs
	 * @param in: The uncompressed file from which to read
	 * Output: an int[] of length 256, whose indices represent ascii character values, and whose values
	 * represent the frequency with which the corresponding ascii character occurs in the input text
	 */
	private int[] countFreqs(BitInputStream in){
		int[] freqs = new int[256];
		int b = in.readBits(BITS_PER_WORD);
		while (b!=-1){
			freqs[b]++;
			b=in.readBits(BITS_PER_WORD);
		}
		in.reset();
		return freqs;
	}

	/*
	 * Populate a PriorityQueue with HuffNodes containing the ascii values and weight
	 * @param int[] freqs: an int[] of length 256, whose indices represent ascii character values, and whose values
	 * represent the frequency with which the corresponding ascii character occurs in the input text
	 * Output: PriorityQueue<HuffNode> with HuffNodes representing the characters in the input file, sorted by 
	 * frequency of occurence
	 */
	private PriorityQueue<HuffNode> populatePQ(int[] freqs){
		PriorityQueue<HuffNode> PQ = new PriorityQueue<HuffNode>();
		for (int i = 0; i < freqs.length; i++){
			if (freqs[i] != 0){
				PQ.add(new HuffNode(i,freqs[i]));
			}
		}
		PQ.add(new HuffNode(PSEUDO_EOF,0));
		return PQ;
	}

	/*
	 * Converts the PriorityQueue of HuffNodes into a HuffTree
	 * @param PriorityQueue<HuffNode> PQ with HuffNodes representing the characters in the input file, sorted by 
	 * frequency of occurence
	 * Output: HuffNode representing the root of the HuffTree
	 */
	private HuffNode makeTree(PriorityQueue<HuffNode> PQ){
		while (PQ.size() > 1){
			HuffNode n1 = PQ.poll();
			HuffNode n2 = PQ.poll();
			PQ.add(new HuffNode(n1.value(),n1.weight()+n2.weight(),n1,n2));
		}
		HuffNode root = PQ.poll();
		return root;
	}

	/*
	 * Writes the header of the output file, representing the HuffTree
	 * @param HuffNode node: the root of the HuffTree
	 * @param BitOutputStream out: the file to write to
	 * Output: void
	 */
	private void writeHeader(HuffNode node, BitOutputStream out){
		out.writeBits(BITS_PER_INT, HUFF_NUMBER);
		writeHeaderRecursive(node,out);
	}

	/*
	 * A helper method for writeHeader
	 * traverses the HuffTree in preorder
	 * @param HuffNode node: the root of the HuffTree
	 * @param BitOutputStream out: the file to write to
	 * Output: void
	 */
	private void writeHeaderRecursive(HuffNode node, BitOutputStream out){
		if (node.left() == null && node.right() == null){
			//node is a leaf
			out.writeBits(1, 1);
			out.writeBits(9, node.value());
			return;
		} else if (node.left() != null && node.right() != null) {
			//node is not a leaf
			out.writeBits(1,0);
			writeHeaderRecursive(node.left(),out);
			writeHeaderRecursive(node.right(),out);
		}
	}

	/*
	 * Writes the body of the file in to the file out, using the Huffman encoding specified by the HuffTree represented by root
	 * @param HuffNode root: a HuffNode representing the root of the HuffTree
	 * @param BitInputStream in: The uncompressed file from which to read
	 * @param BitInputStream out: The file to write the compressed information to
	 * Output: void
	 */
	private void writeBody(HuffNode root, BitInputStream in, BitOutputStream out){
		//populate a map with the huffCodes of each byte value, so we don't have to traverse the tree every time
		HashMap<Integer,String> huffCodes = convertTreeToMap(root);

		int b = in.readBits(BITS_PER_WORD);
		while (b != -1){
			String toWrite = huffCodes.get(b);
			out.writeBits(toWrite.length(), Integer.parseInt(toWrite, 2));
			b = in.readBits(BITS_PER_WORD);
		}
		in.reset();
		//add PSEUDO_EOF to end of file
		String EOFCode = huffCodes.get(PSEUDO_EOF);
		out.writeBits(EOFCode.length(),Integer.parseInt(EOFCode,2));
	}

	/*
	 * A helper method for writeBody
	 * Converts the HuffTree to a HashMap, so writeBody does not have to traverse the HuffTree every time it needs a String
	 * @param HuffNode node: a HuffNode representing the root of the HuffTree to convert
	 * Output: HashMap<Integer,String> mapping the ascii character values to the Strings that are their corresponding huffCodes
	 */
	private HashMap<Integer,String> convertTreeToMap(HuffNode node){
		HashMap<Integer,String> huffCodes = new HashMap<Integer,String>();
		convertTreeToMapRecursive(node,huffCodes,"");
		return huffCodes;
	}

	/*
	 * A helper method for convertTreeToMap
	 * Recursively traverses the HuffTree to determine the code corresponding to each node
	 * @param HuffNode node: a HuffNode representing the root of the HuffTree to convert
	 * @param HashMap<Integer,String> mapping the ascii character values to the Strings that are their corresponding huffCodes
	 * @param String currentCode: a String representing the huffCode at the current node
	 * Output: void
	 */
	private void convertTreeToMapRecursive(HuffNode node, HashMap<Integer,String> huffCodes, String currentCode){
		if (node.left() == null && node.right() == null){
			huffCodes.put(node.value(), currentCode);
			return;
		} else if (node.left() != null && node.right() != null){
			convertTreeToMapRecursive(node.left(),huffCodes,currentCode+"0");			convertTreeToMapRecursive(node.right(),huffCodes,currentCode+"1");

		}
	}

	/*
	 * Decompresses the compressed file represented by BitInputStream in, and writes it to the uncompressed file
	 * represented by BitOutputStream out.
	 * @param BitInputStream in: the compressed file from which to read
	 * @param BitOutputStream out: the uncompressed file to write to
	 * Output: void
	 * @throw HuffException if the file is not in the correct format (does not begin with HUFF_NUMBER or end with PSEUDO_EOF)
	 */
	@Override
	public void decompress(BitInputStream in, BitOutputStream out) {

		//check if the file begins with the proper HUFF_NUMBER. If not, throw an exception.
		if (in.readBits(BITS_PER_INT) != HUFF_NUMBER){
			throw new HuffException("Cannot open file. File is not in correct format.");
		}

		//populate a HashMap that maps the Huffcodes from the tree to the corresponding ascii values
		HashMap<String,Integer> huffMap = decodeHeader(in);

		//use the huffMap to decode the body
		decodeBody(in,out,huffMap);

	}

	/*
	 * Decodes the header of the input file to create a HuffTree, then converts that HuffTree to a HashMap for faster access
	 * @param BitInputStream in: the compressed file from which to read
	 * Output: HashMap<String,Integer> mapping the String huffCode to the ascii character value it represents
	 */
	private HashMap<String,Integer> decodeHeader(BitInputStream in){
		HashMap<String, Integer> huffMap = new HashMap<String, Integer>();
		decodeHeaderRecursive(huffMap,in,"");
		return huffMap;
	}

	/*
	 * A helper method for decodeHeader
	 * Reads the BitInputStream in in a recursive manner to convert the header into a HashMap representing the HuffTree
	 * @param HashMap<String,Integer> huffMap: maps the String huffCode to the ascii character value it represents
	 * @param BitInputStream in: the compressed file from which to read
	 * @param String currentCode: a String representing the huffCode at the current node
	 * Output: void
	 */
	private void decodeHeaderRecursive(HashMap<String,Integer> huffMap, BitInputStream in, String currentCode){
		if (in.readBits(1) == 0){
			decodeHeaderRecursive(huffMap,in,currentCode+"0");
			decodeHeaderRecursive(huffMap,in,currentCode+"1");
		} else {
			huffMap.put(currentCode, in.readBits(9));
		}
	}

	/*
	 * Decodes the body of the file represented by BitInputStream in, and writes the uncompressed information to BitOuputStream out
	 * @param BitInputStream in: the compressed file from which to read
	 * @param BitOutputStream out: the uncompressed file to write to
	 * @param HashMap<String,Integer> huffMap: maps the String huffCode to the ascii character value it represents
	 * Output: void
	 */
	private void decodeBody(BitInputStream in, BitOutputStream out, HashMap<String,Integer> huffMap){
		while (true){
			String code = "";
			boolean found = false;
			while (!found){
				int bitIn = in.readBits(1);
				if (bitIn == 0){
					code += "0";
				} else if (bitIn == 1){
					code += "1";
				} else { //bitIn == -1 or some other unknown value. 
					//This is likely to happen if the file does not end with the PSEUDO_EOF code
					//Having this line here also prevents an infinite loop because even if no part of the file matches
					//any part of the Huffman Tree, this loop will at least stop reading the file when it reaches the end
					throw new HuffException("Error reading file: Could not resolve input file to decode. File might not have ended with PSEUDO_EOF.");
				}
				if (huffMap.containsKey(code)){
					found = true;
				}
			}
			int asciiInt = huffMap.get(code);
			if (asciiInt == PSEUDO_EOF){
				in.reset();
				return;
			} else {
				out.write((char)asciiInt);
			}
		}
	}

}