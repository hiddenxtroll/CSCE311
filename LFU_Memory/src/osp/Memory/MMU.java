/*
 * @OSPProject Memory
 * @author Tien Ho
 * @date 01 December 2015
 * 
 * This class represents the memory-management unit of
 * the simulated computer, and defines the two methods:
 * the initialization method to initialize the frame table
 * and do_refer(), which simulates memory references made by 
 * the CPU.
 */

package osp.Memory;

import java.util.*;

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;

/**
    The MMU class contains the student code that performs the work of
    handling a memory reference.  It is responsible for calling the
    interrupt handler if a page fault is required.

    @OSPProject Memory
*/
public class MMU extends IflMMU {
	public static int[] referenceCount;
    /** 
        This method is called once before the simulation starts. 
	Can be used to initialize the frame table and other static variables.

        @OSPProject Memory
    */
    public static void init() {
        //initialize the frame table
    	int numberOfFrames = MMU.getFrameTableSize();
    	for (int i = 0; i < numberOfFrames; i++) {
    		MMU.setFrame(i, new FrameTableEntry(i));
    	}
    	
    	//initialize the reference array to keep track 
    	//of the reference counts for each frame
    	referenceCount = new int[numberOfFrames];
    	
    	//call the initialization method of the class PageFaultHandler 
    	//at the beginning of the simulation
    	PageFaultHandler.init();
    }

    /**
       This method handlies memory references. The method must 
       calculate, which memory page contains the memoryAddress,
       determine, whether the page is valid, start page fault 
       by making an interrupt if the page is invalid, finally, 
       if the page is still valid, i.e., not swapped out by another 
       thread while this thread was suspended, set its frame
       as referenced and then set it as dirty if necessary.
       (After pagefault, the thread will be placed on the ready queue, 
       and it is possible that some other thread will take away the frame.)
       
       @param memoryAddress A virtual memory address
       @param referenceType The type of memory reference to perform 
       @param thread that does the memory access
       (e.g., MemoryRead or MemoryWrite).
       @return The referenced page.

       @OSPProject Memory
    */
    static public PageTableEntry do_refer(int memoryAddress,
					  int referenceType, ThreadCB thread) {
    	//convert logical address to obtain a page id
        int pageSize = (int) Math.pow(2, MMU.getVirtualAddressBits() - MMU.getPageAddressBits());
        int pageID = memoryAddress / pageSize;
        
        //get the page table of the currently running thread
    	PageTable pageTable = MMU.getPTBR();
    	//get the page to which memoryAddress belongs
    	PageTableEntry referencedPage = pageTable.pages[pageID];
    	
    	//set the appropriate referenced and dirty bits for a valid page
    	if (referencedPage.isValid()) {
    		FrameTableEntry frame = referencedPage.getFrame();
    		frame.setReferenced(true); 
    		//increment the reference count for the frame
    		referenceCount[frame.getID()]++;
    		
    		//the page is marked dirty if it is accessed as MemoryWrite
    		if (referenceType == MemoryWrite) {
    			frame.setDirty(true);
    		} 
    	} else { //invalid page
    		ThreadCB pageFaultThread = referencedPage.getValidatingThread();
    		
    		//Some other thread of the same task has already caused a pagefault 
    		//and the page is already on its way to main memory
    		if (pageFaultThread != null) {
    			thread.suspend(referencedPage);
    			
    			//ensure that the thread is not destroyed while waiting
    			//for the pagefault handler
    			if (thread.getStatus() != ThreadKill) {
    				FrameTableEntry frame = referencedPage.getFrame();
    	    		frame.setReferenced(true);
    	    		//increment the reference count for the frame
    	    		referenceCount[frame.getID()]++;
    	    	
    	    		//the page is marked dirty if it is accessed as MemoryWrite
    	    		if (referenceType == MemoryWrite) {
    	    			frame.setDirty(true);
    	    		} 
    			}
    		} else { //no other thread caused a pagefault
    			//initiate a pagefault
    			InterruptVector.setPage(referencedPage);
    			InterruptVector.setReferenceType(referenceType);
    			InterruptVector.setThread(thread);
    			
    			//this method will invoke the method do_handlePageFault() 
    			//in class PageFaultHandler
    			CPU.interrupt(PageFault);
    			
    			//Again, make sure that the thread is not destroyed while
    			//waiting for completion of I/O
    			if (thread.getStatus() != ThreadKill) {
    				FrameTableEntry frame = referencedPage.getFrame();
    				frame.setReferenced(true);
    				//increment the reference count for the frame
    				referenceCount[frame.getID()]++;
    				
    	    		//the page is marked dirty if it is accessed as MemoryWrite
    	    		if (referenceType == MemoryWrite) {
    	    			frame.setDirty(true);
    	    		} 
    			}
    		}
    	}
    	
    	return referencedPage;
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
     
	@OSPProject Memory
     */
    public static void atError() {
    	
    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
     
      @OSPProject Memory
     */
    public static void atWarning() {

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
