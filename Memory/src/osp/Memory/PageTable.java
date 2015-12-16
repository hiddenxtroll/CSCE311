/*
 * @OSPProject Memory
 * @author Tien Ho
 * @date 12 November 2015
 * 
 * This class implements the page table.  It initializes
 * all the page entries and contains the method do_deallocateMemory()
 * to clear all the frames associated with the task that owns 
 * the page table when it terminates.  
 */

package osp.Memory;
/**
    The PageTable class represents the page table for a given task.
    A PageTable consists of an array of PageTableEntry objects.  This
    page table is of the non-inverted type.

    @OSPProject Memory
*/
import java.lang.Math;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Hardware.*;

public class PageTable extends IflPageTable {
    /** 
	The page table constructor. Must call
	
	    super(ownerTask)

	as its first statement.

	@OSPProject Memory
    */
    public PageTable(TaskCB ownerTask) {
    	super(ownerTask);
    	
    	//calculate the maximum number of pages allowed 
    	int maximumNumberOfPages = (int) Math.pow(2, MMU.getPageAddressBits());
    	this.pages = new PageTableEntry[maximumNumberOfPages];
    	
    	//initialize the entries in the page table
    	for (int i = 0; i < this.pages.length; i++) {
    		this.pages[i] = new PageTableEntry(this, i);
    	}
    }

    /**
       Frees up main memory occupied by the task.
       Then unreserves the freed pages, if necessary.

       @OSPProject Memory
    */
    public void do_deallocateMemory() {	
    	TaskCB task = getTask();

    	//clean all the frames that point to the pages
    	//of the current page table and task
    	for(int i = 0; i < MMU.getFrameTableSize(); i++) {
    		FrameTableEntry frame = MMU.getFrame(i);
    		PageTableEntry page = frame.getPage();
    		
    		//if the page belongs to the current running task
    		if(page != null && page.getTask() == task) {
    			frame.setPage(null);
    			frame.setDirty(false);
    			frame.setReferenced(false);
    			//unreserve the frame from the current task
    			if (frame.getReserved() == task) {
    				frame.setUnreserved(task);
    			}
    		}
    	}
    }
    /*
       Feel free to add methods/fields to improve the readability of your code
    */
}


/*
      Feel free to add local classes to improve the readability of your code
*/
