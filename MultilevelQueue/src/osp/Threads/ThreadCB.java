/*
 * @OSPProject Threads
 * @author Tien Ho
 * @date 27 October 2015
 * 
 * This class contains the methods to manage the activities of threads 
 * such as creating a new thread, destroying and suspending the running 
 * and waiting threads, and resuming the suspended thread.  The class 
 * uses round-robin CPU scheduling to perform context switching 
 * among threads using the multi-level queue.  This queue consists of 
 * the high and low priority queues.  Threads in the high priority queue
 * are always dispatched first.  
 */
package osp.Threads;

import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
 * This class is responsible for actions related to threads, including creating,
 * killing, dispatching, resuming, and suspending threads.
 * 
 * @OSPProject Threads
 */
public class ThreadCB extends IflThreadCB {
	public static GenericList highPriorityQueue;
	public static GenericList lowPriorityQueue;
	
	public int lastCpuBurst;
	public long lastDispatch;
	/**
	 * The thread constructor. Must call
	 * 
	 * super();
	 * 
	 * as its first statement.
	 * 
	 * @OSPProject Threads
	 */
	public ThreadCB() {
		super();
		lastCpuBurst = -1;
		lastDispatch = -1;
	}

	/**
	 * This method will be called once at the beginning of the simulation. The
	 * student can set up static variables here.
	 * 
	 * @OSPProject Threads
	 */
	public static void init() {
		//Initialize the high priority and low priority queues used to store the ready threads
		highPriorityQueue = new GenericList();
		lowPriorityQueue = new GenericList();
	}

	/**
	 * Sets up a new thread and adds it to the given task. The method must set
	 * the ready status and attempt to add thread to task. If the latter fails
	 * because there are already too many threads in this task, so does this
	 * method, otherwise, the thread is appended to the ready queue and
	 * dispatch() is called.
	 * 
	 * The priority of the thread can be set using the getPriority/setPriority
	 * methods. However, OSP itself doesn't care what the actual value of the
	 * priority is. These methods are just provided in case priority scheduling
	 * is required.
	 * 
	 * @return thread or null
	 * @OSPProject Threads
	 */
	static public ThreadCB do_create(TaskCB task) {
		//Dispatch a new thread anyway for a null task
		if (task == null) {
			dispatch();
			return null;
		}
		
		//Ensure that the number of threads per task is not exceeded before
		//a new thread is created for that task
		if (task.getThreadCount() >= MaxThreadsPerTask) {
			dispatch();
			return null;
		}

		//Instantiate a new thread and associate it with the specified task
		ThreadCB newThread = new ThreadCB();
		newThread.setStatus(ThreadReady);
		newThread.setPriority(task.getPriority());
		newThread.setTask(task);

		//Make sure that the task is able to add a new thread 
		//to its existing list of threads
		if (task.addThread(newThread) == FAILURE) {
			dispatch();
			return null;
		}
		
		//Place this new thread to the high priority queue because
		//there are no previous values from which to calculate he last CPU burst
		highPriorityQueue.append(newThread);
		
		//Regardless of whether the new thread is created successfully or not,
		//a new thread should always be dispatched to optimize CPU usage.
		dispatch();

		return newThread;
	}

	/**
	 * Kills the specified thread.
	 * 
	 * The status must be set to ThreadKill, the thread must be removed from the
	 * task's list of threads and its pending IORBs must be purged from all
	 * device queues.
	 * 
	 * If some thread was on the ready queue, it must removed, if the thread was
	 * running, the processor becomes idle, and dispatch() must be called to
	 * resume a waiting thread.
	 * 
	 * @OSPProject Threads
	 */
	public void do_kill() {	  
		//Remove the ready thread from the multi-level queue
		if (getStatus() == ThreadReady) {
			if (highPriorityQueue.contains(this)) {
				highPriorityQueue.remove(this);
			} else if (lowPriorityQueue.contains(this)) {
				lowPriorityQueue.remove(this);
			}
			
		} else if (getStatus() == ThreadRunning) {
			//Preempt the running thread
			if (MMU.getPTBR() != null) { //make sure that some thread is running
				ThreadCB currentlyRunningThread = MMU.getPTBR().getTask().getCurrentThread();
				//Determine the currently running thread  
				//If the running thread considered for destruction
				//is matches with what OSP2 thinks is the current thread, 
				//then its control of CPU must be removed.
				if (this == currentlyRunningThread) {
					MMU.setPTBR(null);
					getTask().setCurrentThread(null);
				} 
			}
		}

		//Remove the thread from its task
		getTask().removeThread(this);
		setStatus(ThreadKill);

		//Cancel the I/O request issued by the dead thread by removing
		//the corresponding IORB from its device queue
		for (int i = 0; i < Device.getTableSize(); i++) {
			Device.get(i).cancelPendingIO(this);
		}

		//Release all the resources acquired by the dead thread 
		//into the common pool so that other threads could use them
		ResourceCB.giveupResources(this);
		
		//Dispatches a new thread
		dispatch();

		//Kills the task with no threads left
		if (getTask().getThreadCount() == 0) {
			getTask().kill();
		}
	}

	/**
	 * Suspends the thread that is currently on the processor on the specified
	 * event.
	 * 
	 * Note that the thread being suspended doesn't need to be running. It can
	 * also be waiting for completion of a pagefault and be suspended on the
	 * IORB that is bringing the page in.
	 * 
	 * Thread's status must be changed to ThreadWaiting or higher, the processor
	 * set to idle, the thread must be in the right waiting queue, and
	 * dispatch() must be called to give CPU control to some other thread.
	 * 
	 * @param event
	 *            - event on which to suspend this thread.
	 * @OSPProject Threads
	 */
	public void do_suspend(Event event) {
		//Suspends the running thread
		if (getStatus() == ThreadRunning) {
			if (MMU.getPTBR() != null) {
				ThreadCB currentlyRunningThread = MMU.getPTBR().getTask().getCurrentThread();
				//If a running thread is being suspended, it must be removed from controlling the CPU.
				if (this == currentlyRunningThread) {
					MMU.setPTBR(null);
				} 
			}

			//Sets the current thread of its task to null
			getTask().setCurrentThread(null);
			setStatus(ThreadWaiting);
		} else if (getStatus() >= ThreadWaiting) {
			//Increment the waiting status of an already-waiting thread
			setStatus(getStatus()+1);
		}

		//Ensure that the suspended thread is not on the multi-level queue
		if (highPriorityQueue.contains(this)) {
			highPriorityQueue.remove(this);
		} else if (lowPriorityQueue.contains(this)) {
			lowPriorityQueue.remove(this);
		}

		//Place the suspended thread on the waiting queue to the event
		event.addThread(this);

		//Dispatch a new thread
		dispatch();
	}

	/**
	 * Resumes the thread.
	 * 
	 * Only a thread with the status ThreadWaiting or higher can be resumed. The
	 * status must be set to ThreadReady or decremented, respectively. A ready
	 * thread should be placed on the ready queue.
	 * 
	 * @OSPProject Threads
	 */
	public void do_resume() {
		if (getStatus() < ThreadWaiting) {
			MyOut.print(this, "Attempt to resume " + this
					+ ", which wasn't waiting");
			return;
		}

		//Set thread's status
		if (getStatus() == ThreadWaiting) {
			//Resume a waiting thread to its ready state
			setStatus(ThreadReady);
		} else if (getStatus() > ThreadWaiting){
			//Decrement the waiting status if it is currently at a lower level
			//than the standard waiting state
			setStatus(getStatus()-1);
		} 

		//Put the thread on the multi-level queue if its status is ready
		if (getStatus() == ThreadReady) {
			//Place the thread on the high priority queue if its last CPU burst
			//is less than the quantum value of 30. Otherwise, place it on 
			//the low priority queue
			if (lastCpuBurst <= 30) {
				highPriorityQueue.append(this);
			} else {
				lowPriorityQueue.append(this);
			}
		}

		//Dispatch a new thread
		dispatch();
	}

	/**
	 * Selects a thread from the run queue and dispatches it.
	 * 
	 * If there is just one thread ready to run, reschedule the thread
	 * currently on the processor.
	 * 
	 * In addition to setting the correct thread status it must update the PTBR.
	 * 
	 * @return SUCCESS or FAILURE
	 * @OSPProject Threads
	 */
	public static int do_dispatch() {
		//Preempt the current thread if appropriate by removing it from CPU control
		//and from its task
		if (MMU.getPTBR() != null) {
			ThreadCB currentlyRunningThread = MMU.getPTBR().getTask().getCurrentThread();
			currentlyRunningThread.getTask().setCurrentThread(null);
			MMU.setPTBR(null);
			currentlyRunningThread.setStatus(ThreadReady);
			
			//Calculate the lastCpuBurst of the preempted thread
			currentlyRunningThread.lastCpuBurst = (int) ((long) HClock.get() - currentlyRunningThread.lastDispatch);
			
			//Append the preempted thread to the multi-level queue
			//Place the thread on the high priority queue if its last CPU burst
			//is less than the quantum value of 30. Otherwise, place it on 
			//the low priority queue
			if (currentlyRunningThread.lastCpuBurst <= 30) {
				highPriorityQueue.append(currentlyRunningThread);
			} else {
				lowPriorityQueue.append(currentlyRunningThread);
			}
		}

		//Set the page table base register (PTBR) to null 
		//if the readyQueue is empty and there is no new
		//thread to dispatch
		if (highPriorityQueue.isEmpty() && lowPriorityQueue.isEmpty()) {
			MMU.setPTBR(null);
			return FAILURE;
		} 
		
		if (!highPriorityQueue.isEmpty()) {
			ThreadCB newThread = (ThreadCB) highPriorityQueue.removeHead();

			//Set MMU to point to the page table of the dispatched thread
			MMU.setPTBR(newThread.getTask().getPageTable());
			
			//Set the thread as the current thread of its task
			newThread.getTask().setCurrentThread(newThread);
			
			newThread.setStatus(ThreadRunning);
			
			//Set the time of dispatch
			newThread.lastDispatch = HClock.get();
			
			//Set the interrupt timer using a quantum of 50 for round-robin scheduling
			HTimer.set(30);
		} else if (!lowPriorityQueue.isEmpty()) {
			ThreadCB newThread = (ThreadCB) lowPriorityQueue.removeHead();

			//Set MMU to point to the page table of the dispatched thread
			MMU.setPTBR(newThread.getTask().getPageTable());
			
			//Set the thread as the current thread of its task
			newThread.getTask().setCurrentThread(newThread);
			
			newThread.setStatus(ThreadRunning);
			//Get the time of dispatch
			newThread.lastDispatch = HClock.get();
			
			//Set the interrupt timer using a quantum of 50 for round-robin scheduling
			HTimer.set(100);
		}

		return SUCCESS;
	}

	/**
	 * Called by OSP after printing an error message. The student can insert
	 * code here to print various tables and data structures in their state just
	 * after the error happened. The body can be left empty, if this feature is
	 * not used.
	 * 
	 * @OSPProject Threads
	 */
	public static void atError() {
	}

	/**
	 * Called by OSP after printing a warning message. The student can insert
	 * code here to print various tables and data structures in their state just
	 * after the warning happened. The body can be left empty, if this feature
	 * is not used.
	 * 
	 * @OSPProject Threads
	 */
	public static void atWarning() {
	}

	/*
	 * Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */
