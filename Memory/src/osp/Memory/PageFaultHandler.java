/*
 * @OSPProject Memory
 * @author Tien Ho
 * @date 12 November 2015
 * 
 * This class contains the method to handle the pagefault
 * on an invalid page by finding an available frame for it.  
 * If there are no free frames remaining, then the FIFO
 * page replacement algorithm is used to determine a victim page 
 * and evict its frame given that the frame is not locked and 
 * reserved.    
 */

package osp.Memory;

import java.util.*;

import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;

/**
    The page fault handler is responsible for handling a page
    fault.  If a swap in or swap out operation is required, the page fault
    handler must request the operation.

    @OSPProject Memory
*/
public class PageFaultHandler extends IflPageFaultHandler {
    /**
        This method handles a page fault. 

        It must check and return if the page is valid, 

        It must check if the page is already being brought in by some other
	thread, i.e., if the page's has already pagefaulted
	(for instance, using getValidatingThread()).
        If that is the case, the thread must be suspended on that page.
        
        If none of the above is true, a new frame must be chosen 
        and reserved until the swap in of the requested 
        page into this frame is complete. 

	Note that you have to make sure that the validating thread of
	a page is set correctly. To this end, you must set the page's
	validating thread using setValidatingThread() when a pagefault
	happens and you must set it back to null when the pagefault is over.

        If a swap-out is necessary (because the chosen frame is
        dirty), the victim page must be dissasociated 
        from the frame and marked invalid. After the swap-in, the 
        frame must be marked clean. The swap-ins and swap-outs 
        must are preformed using regular calls read() and write().

        The student implementation should define additional methods, e.g, 
        a method to search for an available frame.

	Note: multiple threads might be waiting for completion of the
	page fault. The thread that initiated the pagefault would be
	waiting on the IORBs that are tasked to bring the page in (and
	to free the frame during the swapout). However, while
	pagefault is in progress, other threads might request the same
	page. Those threads won't cause another pagefault, of course,
	but they would enqueue themselves on the page (a page is also
	an Event!), waiting for the completion of the original
	pagefault. It is thus important to call notifyThreads() on the
	page at the end -- regardless of whether the pagefault
	succeeded in bringing the page in or not.

        @param thread the thread that requested a page fault
        @param referenceType whether it is memory read or write
        @param page the memory page 

	@return SUCCESS is everything is fine; FAILURE if the thread
	dies while waiting for swap in or swap out or if the page is
	already in memory and no page fault was necessary (well, this
	shouldn't happen, but...). In addition, if there is no frame
	that can be allocated to satisfy the page fault, then it
	should return NotEnoughMemory

        @OSPProject Memory
    */
	
	public static int frameCursor;
	public static final int NUMBER_OF_FRAMES = MMU.getFrameTableSize();
	
    /**
     * Initialize the cursor used to keep track
     * of the FIFO access to the frame table
     * 
     * @return none
     */
    public static void init() {	
    	frameCursor = 0;
    }  
    
    /**
     * Handle the pagefault on an invalid page by either
     * locating a free frame or perform a FIFO algorithm
     * to evict a frame from other pages.
     * 
     * @param thread
     * @param referenceType
     * @param page
     * @return SUCCESS if the pagefault handler can find 
     * an available frame for the invalid page and the thread
     * is not killed during the process, else FAILURE
     */
    public static int do_handlePageFault(ThreadCB thread, 
					 int referenceType,
					 PageTableEntry page)  {	
    	//check if the page is valid or all frames are locked/reserved
    	if (page.isValid()) {
    		//all threads that might be waiting on the page must be resumed
    		page.notifyThreads();
        	ThreadCB.dispatch();
        	
    		return FAILURE;
    	} else if (!PageFaultHandler.hasUnlockedAndUnreservedFrames()) {
    		//all threads that might be waiting on the page must be resumed
        	page.notifyThreads();
        	ThreadCB.dispatch();
        	
    		return NotEnoughMemory;
    	} 

    	page.setValidatingThread(thread);
    	SystemEvent pagefaultEvent = new SystemEvent("Pagefault");
    	thread.suspend(pagefaultEvent);
    	
    	//check if there are any free frames
    	FrameTableEntry freeFrame = PageFaultHandler.getAFreeFrame();
    	//get the task that owns the page
    	TaskCB task = page.getTask();
    	FrameTableEntry availableFrame = null;
    	
    	//if there is a free frame: 
    	if (freeFrame != null) {
    		//reserve the frame
    		availableFrame = freeFrame;
    		availableFrame.setReserved(task);
    		
    		//link the frame to the page
			page.setFrame(availableFrame);
    		availableFrame.setPage(page);
    		
    		//perform a swap-in
    		task.getSwapFile().read(page.getID(), page, thread);
    	} else { //no free frames are available
    		//perform the FIFO algorithm to evict a page
    		availableFrame = PageFaultHandler.performFIFO();
    		availableFrame.setReserved(task);
    		
    		//get the page that is currently associated with the frame
    		PageTableEntry oldPage = availableFrame.getPage();
    		TaskCB oldTask = oldPage.getTask();
    		
    		//if the frame is dirty
    		if (availableFrame.isDirty()) {
    			//perform a swap-out on the task that owns 
    			//the page that currently occupies the frame
    			oldTask.getSwapFile().write(oldPage.getID(), oldPage, thread);
    			    
    			//ensure that the thread is not killed after the swap-in.
    			if (thread.getStatus() == ThreadKill) {
    				page.notifyThreads();
    				//resume the thread that is suspended on the pagefault event
    				pagefaultEvent.notifyThreads();
    				page.setValidatingThread(null);
    				ThreadCB.dispatch();
    				
    				return FAILURE;
    			}  			
    		}
    		
    		//free the frame by disassociating
    		//it from the old page and clear its
    		//referenced and dirty bits
    		availableFrame.setPage(null);
    		availableFrame.setDirty(false);
    		availableFrame.setReferenced(false);
    		oldPage.setValid(false);
    		oldPage.setFrame(null);
    		
    		//link the frame to the page
    		page.setFrame(availableFrame);
    		availableFrame.setPage(page);

    		//perform a swap-in
    		task.getSwapFile().read(page.getID(), page, thread);
    	}
    	
    	//check for dead threads after swap-in and swap-out
		if (thread.getStatus() == ThreadKill) {
			page.notifyThreads();
			pagefaultEvent.notifyThreads();
			page.setValidatingThread(null);
			ThreadCB.dispatch();
			
			return FAILURE;
		} else {
			//update the page table
    		page.setValid(true);
    		if (referenceType == MemoryWrite) {
    			availableFrame.setDirty(true);
    		} 
    		
    		//unreserve the frame from the page task
    		availableFrame.setUnreserved(task);
    		page.notifyThreads();
    		pagefaultEvent.notifyThreads();
    		page.setValidatingThread(null);
    		ThreadCB.dispatch();
    		
    		return SUCCESS;
		}
    }
   
    /**
     * Check if there is any free frame and return it.
     * A free frame is indicated as having no associated page.
     * 
     * @return free frame 
     */
    public static FrameTableEntry getAFreeFrame() {
    	FrameTableEntry freeFrame = null;
    	for (int i = 0; i < MMU.getFrameTableSize(); i++) {
    		FrameTableEntry frame = MMU.getFrame(i);
    		if (frame.getPage() == null && !frame.isReserved()) {
    			freeFrame = frame;
    			break;
    		}
    	}
    	
    	return freeFrame;
    }
    
    /**
     * Check if the current page table has any unlocked
     * or unreserved frames.
     * 
     * @return true if there exists a frame that is neither
     * locked nor reserved, else false
     */
    public static boolean hasUnlockedAndUnreservedFrames() {
    	int numberOfLockedOrReservedFrames = 0;
    	for (int i = 0; i < MMU.getFrameTableSize(); i++) {
    		FrameTableEntry frame = MMU.getFrame(i);
    		//check if a frame is either locked or reserved
    		if (frame.getLockCount() > 0 || frame.isReserved()) {
    			numberOfLockedOrReservedFrames++;
    		}
    	}
    	
    	if (numberOfLockedOrReservedFrames == MMU.getFrameTableSize()) {
    		return false;
    	}
    	
    	return true;
    }
    
    /**
     * Perform an approximate FIFO page replacement algorithm 
     * to evict a frame for the invalid page. An integer cursor
     * is used to keep track of the order of the frames to be 
     * next replaced.  The cursor is incremented every time the 
     * page to which it points is either locked or reserved.  
     * The cursor is incremented regardless at the end of the method 
     * to point to the next frame to be considered for replacement.   
     * 
     * @return a frame that will be used for the invalid page
     */
    public static FrameTableEntry performFIFO() {
    	FrameTableEntry victimFrame = MMU.getFrame(frameCursor);
    	
    	//as long as the victim page is locked or reserved, move the cursor to the next frame
    	while (victimFrame.getLockCount() > 0 || victimFrame.isReserved()) {
    		frameCursor = ++frameCursor % NUMBER_OF_FRAMES;
    		victimFrame = MMU.getFrame(frameCursor);
    	}
    	
    	//increment the cursor to point to the next frame
    	frameCursor = ++frameCursor % NUMBER_OF_FRAMES;

    	return victimFrame;
    }
    /*
       Feel free to add methods/fields to improve the readability of your code
    */
}

/*
      Feel free to add local classes to improve the readability of your code
*/

