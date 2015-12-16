/*
 * @OSPProject Threads
 * @author Tien Ho
 * @date 27 October 2015
 * 
 * This class defines the interrupt handler when the system timer expires.
 */


package osp.Threads;
import osp.IFLModules.*;
import osp.Memory.MMU;
import osp.Utilities.*;
import osp.Hardware.*;

/**    
     *  The timer interrupt handler.  This class is called upon to
     * handle timer interrupts.
*
    *  @OSPProject Threads
*/
public class TimerInterruptHandler extends IflTimerInterruptHandler
{
    /**
      * This basically only needs to reset the times and dispatch
      * another process.
*
      * @OSPProject Threads
    */
    public void do_handleInterrupt()
    {
    	//Dispatch a new thread
    	ThreadCB.dispatch();
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
