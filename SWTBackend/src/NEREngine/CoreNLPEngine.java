/**
 * 
 */
package NEREngine;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sascha Ulbrich
 *
 */
public class CoreNLPEngine implements NEREngine {
	private static CoreNLPEngine engine;
	private StanfordCoreNLP pipeline;
	private static final Logger LOG = LoggerFactory.getLogger(CoreNLPEngine.class);
	
	/*
	 * Singleton to ensure that CoreNLP is initialized just once
	 */
	private CoreNLPEngine(){
		//Initialize CoreNLP 
		setPropertiesForStanfordCoreNLP();
	}
	
	public static CoreNLPEngine getInstance() {
		if (CoreNLPEngine.engine == null) {
				CoreNLPEngine.engine = new CoreNLPEngine ();
		    }
		return CoreNLPEngine.engine;
	}
	
	private void setPropertiesForStanfordCoreNLP(){
        Properties props = new Properties();
        boolean useRegexner = false;
        if (useRegexner) {
          props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner");
          props.put("regexner.mapping", "locations.txt");
        } else {
          props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
        }
        this.pipeline = new StanfordCoreNLP(props);
    }

	/* (non-Javadoc)
	 * @see NEREngine.NEREngine#getEntitiesFromText(java.lang.String)
	 */
	@Override
	public List<String> getEntitiesFromText(String text) {
		// Analyze string
		//http://www.informit.com/articles/article.aspx?p=2265404
		//this.pipeline.clearAnnotatorPool();
		
		final List<String> entities = new ArrayList<String>();

        //replace intra-word ".", ":", "/"
        text = this.splitOnIntrawordPunctuation(text);

        //normalize punctuation to improve negation detection
        text = this.cleanNegators(text);

        Annotation document = new Annotation(text);
        // run all Annotators on this text
        this.pipeline.annotate(document);

        // these are all the sentences in this document
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        StringBuilder sb = new StringBuilder();
        List<EmbeddedToken> tokens = new ArrayList<EmbeddedToken>();
        
        for (CoreMap sentence : sentences) {
            // traversing the words in the current sentence, "O" is a sensible default to initialise
            // tokens to since we're not interested in unclassified / unknown things..
            String prevNeToken = "O";
            String currNeToken = "O";
            boolean newToken = true;
            for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
              currNeToken = token.get(NamedEntityTagAnnotation.class);
              String word = token.get(TextAnnotation.class);
              // Strip out "O"s completely, makes code below easier to understand
              if (currNeToken.equals("O")) {
                // LOG.debug("Skipping '{}' classified as {}", word, currNeToken);
                if (!prevNeToken.equals("O") && (sb.length() > 0)) {
                  handleEntity(prevNeToken, sb, tokens);
                  newToken = true;
                }
                continue;
              }

              if (newToken) {
                prevNeToken = currNeToken;
                newToken = false;
                sb.append(word);
                continue;
              }

              if (currNeToken.equals(prevNeToken)) {
                sb.append(" " + word);
              } else {
                // We're done with the current entity - print it out and reset
                // TODO save this token into an appropriate ADT to return for useful processing..
                handleEntity(prevNeToken, sb, tokens);
                newToken = true;
              }
              prevNeToken = currNeToken;
            }
        }
        
        //TODO refine return
        for (EmbeddedToken t : tokens) {
			entities.add(t.getValue() + ": " + t.getName());
		}      
        return entities;
	}
	private void handleEntity(String inKey, StringBuilder inSb, List<EmbeddedToken> inTokens) {
	    LOG.debug("'{}' is a {}", inSb, inKey);
	    inTokens.add(new EmbeddedToken(inKey, inSb.toString()));
	    inSb.setLength(0);
	  }
        
	
	 private String splitOnIntrawordPunctuation(String t) {
	        //replace intra-word ".", ":", "/" (\S is non-whitespace character)
	        return t.replaceAll("(\\S)(\\.|:|/)(\\S)", "$1$2 $3");
	    }
	 
	 private String cleanNegators(String t) {
	        //´ and ` with normalized '
	        t = t.replaceAll("[´`]", "'");
	        // insert ' where it is missing
	        t = t.replaceAll("\\b" +
	                "(do|does|dos|doe|did" +
	                "|have|hav|has|hase|had" +
	                "|wo|is|are|was|were|wer" +
	                "|ca|could" +
	                "|would|should" +
	                "must)" +
	                "(n)(t)\\b"
	                , "$1$2'$3");
	        return t;
	    }

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		NEREngine e = CoreNLPEngine.getInstance();
		String text = "This is a test to identify SAP in Walldorf with Hasso Plattner as founder.";
		List<String> l = e.getEntitiesFromText(text);
		for (String s : l) {
			System.out.println(s);
		}				

	}
	
	class EmbeddedToken {

		  private String name;
		  private String value;

		  public String getName() {
		    return name;
		  }

		  public String getValue() {
		    return value;
		  }

		  public EmbeddedToken(String name, String value) {
		    super();
		    this.name = name;
		    this.value = value;
		  }
		}

}
