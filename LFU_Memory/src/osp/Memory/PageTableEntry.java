/*
 * @OSPProject Memory
 * @author Tien Ho
 * @date 01 December 2015
 * 
 * This class implements the entries in the page table.  It contains
 * methods to increment and decrement the lock count of the frame 
 * associated with the page in order to protect the frame that is
 * active in I/O operations from being evicted for an invalid page.
 * The page with a positive lock count is considered locked.
 */

package osp.Memory;

import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;
/**
   The PageTableEntry object contains information about a specific virtual
   page in memory, including the page frame in which it resides.
   
   @OSPProject Memory

*/

public class PageTableEntry extends IflPageTableEntry {
    /**
       The constructor. Must call

       	   super(ownerPageTable,pageNumber);
	   
       as its first statement.

       @OSPProject Memory
    */
    public PageTableEntry(PageTable ownerPageTable, int pageNumber) {
    	super(ownerPageTable, pageNumber);
    }

    /**
       This method increases the lock count on the page by one. 

	The method must FIRST increment lockCount, THEN  
	check if the page is valid, and if it is not and no 
	page validation event is present for the page, start page fault 
	by calling PageFaultHandler.handlePageFault().

	@return SUCCESS or FAILURE
	FAILURE happens when the pagefault due to locking fails or the 
	that created the IORB thread gets killed.

	@OSPProject Memory
     */
    public int do_lock(IORB iorb) {
    	if (!this.isValid()) {
    		//get the thread that requested the I/O
    		ThreadCB iOThread = iorb.getThread();
    		
    		//if there is not yet a thread that caused the pagefault on the page
    		if (this.getValidatingThread() == null) {
    			//initiate the pagefault handler
    			PageFaultHandler.handlePageFault(iOThread, MemoryLock, this);
    			
    			//check if the page becomes valid and the IO thread is not killed
    			if (iOThread.getStatus() != ThreadKill) {
    				//increment the lock count
    				this.getFrame().incrementLockCount();
    				return SUCCESS;
    			} else {
    				return FAILURE;
    			}
    		} else { //if the page is being loaded into memory
    			//get the thread that caused the pagefault
        		ThreadCB pageFaultThread = this.getValidatingThread();
        		
        		//check if the thread that caused a pagefault is
        		//the same as the one that request a lock
        		if (iOThread != pageFaultThread) {
        			iOThread.suspend(this);
        			
        			//check if the page becomes valid and the IO thread is not killed
        			if (iOThread.getStatus() != ThreadKill) {
        				this.getFrame().incrementLockCount();
        				return SUCCESS;
        			} else {
        				return FAILURE;
        			}
        		} else { 
        			this.getFrame().incrementLockCount();
        			return SUCCESS;
        		}    		
    		}  		
    	} else {
    		this.getFrame().incrementLockCount();
    		return SUCCESS;
    	}
    }

    /** This method decreases the lock count on the page by one. 

	This method must decrement lockCount, but not below zero.

	@OSPProject Memory
    */
    public void do_unlock() {
    	FrameTableEntry frame = this.getFrame();
    	//make sure that the lock count does not become negative
    	//before decrementing the lock count
    	if (frame.getLockCount() > 0) {
    		frame.decrementLockCount();
    	}
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
