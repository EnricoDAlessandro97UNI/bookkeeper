package org.apache.bookkeeper.bookie.storage.ldb;


import java.util.Arrays;
import java.util.Collection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;


/**
 * @author Enrico D'Alessandro - University of Rome Tor Vergata 
 */
@RunWith(Enclosed.class)
public class WriteCacheTests {

	private static final long LEDGER_ID = 0;
	private static final long EXISTING_ENTRY_ID = 1;
	private static final long NON_EXISTING_ENTRY_ID = 0;
	private static final long MAX_CACHE_SIZE = 4*1024;
	private static final int MAX_SEGMENT_SIZE = 1024;

	
	@RunWith(Parameterized.class)
	public static class WriteCachePutTest {
		
		private long ledgerId;
		private long entryId;
		private ByteBuf entry;
		private boolean expected;
		
		private WriteCache cache;
		private boolean existsMaxSegmentSize;
		
		//costruttore
	    public WriteCachePutTest(long ledgerId, long entryId, Integer entrySize, boolean expected, boolean existsMaxSegmentSize) {
	        configure(ledgerId, entryId, entrySize, expected, existsMaxSegmentSize);
	    }

	    public void configure(long ledgerId, long entryId, Integer entrySize, boolean expected, boolean existsMaxSegmentSize) {
	        this.expected = expected;
	        this.ledgerId = ledgerId;
	        this.entryId = entryId;
	        if(entrySize != null)
	            this.entry = Unpooled.wrappedBuffer(new byte[entrySize]);
	        else this.entry = null;
	        this.existsMaxSegmentSize = existsMaxSegmentSize;
	    }

	    @Parameterized.Parameters
	    public static Collection<?> getTestParameters() {
			return Arrays.asList(new Object[][] {
	    		// LEDGER_ID        ENTRY_ID                ENTRY_SIZE		EXPECTED	MAX_SEGMENT_SIZE
				
				// configurazione valida
	        	{  LEDGER_ID,		EXISTING_ENTRY_ID,		1024,			true,    	false  }, // ok
	        	
	        	// entry > maxCacheSize
	        	{  LEDGER_ID,		EXISTING_ENTRY_ID,		6*1024,			false,   	false  }, 
	        	
	        	// entry null
	        	{  LEDGER_ID,       EXISTING_ENTRY_ID,      null,       	false,   	false  }, 
	        	
	        	// entryId non esistente e varianti
	        	{  LEDGER_ID,       NON_EXISTING_ENTRY_ID,      				1024,       	true,    	false  }, // ok
	        	{  LEDGER_ID,       NON_EXISTING_ENTRY_ID,      				6*1024,    		false,   	false  },	
	        	{  LEDGER_ID,       NON_EXISTING_ENTRY_ID,  					null,        	false,  	false  },
	        	
	        	// entryId negativo e varianti
	        	{  LEDGER_ID,      -1,						1024,       	true,    	false  }, // ok, ma non viene fatto un controllo su entryId > 0 
	        	{  LEDGER_ID,      -1,						6*1024,    		false,   	false  },
	        	{  LEDGER_ID,      -1,      				null,        	false,   	false  },
	        	
	        	// ledgerId negativo e varianti
	        	{ -1,             	EXISTING_ENTRY_ID,      1024,       	false,   	false  },
	        	{ -1,             	EXISTING_ENTRY_ID,      6*1024,      	false,   	false  },
	        	{ -1,  				EXISTING_ENTRY_ID,      null,        	false,   	false  },
	        	
	        	{ -1,  	   			NON_EXISTING_ENTRY_ID,  1024,       	false,   	false  },
	        	{ -1,  	   			NON_EXISTING_ENTRY_ID,  6*1024,      	false,   	false  },
	        	{ -1,  				NON_EXISTING_ENTRY_ID,  null,        	false,   	false  },
	        	
	        	{ -1,  			   -1,               		1024,       	false,   	false  },
	        	{ -1,  			   -1,               		6*1024,      	false,   	false  },
	        	{ -1,  			   -1,               		null,        	false,   	false  },
	        	
	        	// for coverage
	        	{  1,  			    NON_EXISTING_ENTRY_ID,  2*1024,        	false,   	true   }, // jacoco: maxSegSize - localOffset < size
	        	{  1,  			    NON_EXISTING_ENTRY_ID,  1024,        	true,   	true   }  // jacoco: maxSegSize - localOffset = size
	        });
	    }

		@Before
		public void setUp() {
			if (existsMaxSegmentSize) {
				cache = new WriteCache(UnpooledByteBufAllocator.DEFAULT, MAX_CACHE_SIZE, MAX_SEGMENT_SIZE); 
			} 
			else {
				cache = new WriteCache(UnpooledByteBufAllocator.DEFAULT, MAX_CACHE_SIZE);
			}
			
			if (this.ledgerId == LEDGER_ID) {
				ByteBuf initialEntry = Unpooled.wrappedBuffer("initial-entry".getBytes());
				initialEntry.writerIndex(initialEntry.capacity());
				cache.put(LEDGER_ID, EXISTING_ENTRY_ID, initialEntry);
				if (!cache.get(LEDGER_ID, EXISTING_ENTRY_ID).equals(initialEntry))
					Assert.fail("Initial state configuration should be done");
			}
		}
		
		@Test
		public void testPut() {
			boolean actual;
			try {
				cache.put(ledgerId, entryId, entry);
				actual = cache.get(ledgerId, entryId).equals(entry);
			} catch (NullPointerException | IllegalArgumentException e) {
				actual = false;
			}
			Assert.assertEquals(this.expected, actual);
		}
		
		@After
		public void tearDown() {
			cache.clear();
			cache.close();
		}
		
	}	

}