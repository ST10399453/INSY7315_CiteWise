package com.example.citewise_mobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ConsultantDashboardActivity : AppCompatActivity() {

    // urgency enum for filtering
    enum class Urgency {ALL, URGENT, MEDIUM, LOW}

    // sample data
    data class Task(val name: String, val urgency:Urgency)

    // store full list and currently filtered list
    private lateinit var fullTaskList: List<Task>
  //  private lateinit var taskAdapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_consultant_dashboard)

        // recycler view
        val recyclerView = findViewById<RecyclerView>(R.id.tasksRecyclerView)
     //   taskAdapter = TaskAdapter(fullTaskList)
        recyclerView.layoutManager = LinearLayoutManager(this)
     //   recyclerView.adapter = taskAdapter

        // radio group listener
        val radioGroup = findViewById<RadioGroup>(R.id.urgencyFilterGroup)
        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedUrgency = when (checkedId){
                R.id.filterUrgent -> Urgency.URGENT
                R.id.filterMedium -> Urgency.MEDIUM
                R.id.filterLow -> Urgency.LOW
                R.id.filterAll -> Urgency.ALL
                else -> Urgency.ALL
            }
            // call filtering function
            filterTasks(selectedUrgency)
        }
    }

    // Filters tasks based on urgency and updates recycler view
    private fun filterTasks(urgency: Urgency){
        val filteredList = if (urgency == Urgency.ALL){
            fullTaskList
        }
        else{
            fullTaskList.filter{it.urgency == urgency}
        }
    //    taskAdapter.updateList(filteredList)
    }

    private fun getSampleTasks(): List<Task>{
        return listOf(
            Task("Review document", Urgency.MEDIUM),
            Task("Proofreading & Editing", Urgency.URGENT),
            Task("Referencing", Urgency.LOW)
        )
    }

     // class TaskAdapter(private var tasks: List<Task>): RecyclerView.Adapter<TaskAdapter.TaskViewHolder>(){

        class TaskViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
            val urgencyTag: TextView = itemView.findViewById(R.id.taskUrgencyTag)
            val serviceType: TextView = itemView.findViewById(R.id.taskServiceType)
            val studentName: TextView = itemView.findViewById(R.id.taskStudentName)
            val progressBar: ProgressBar = itemView.findViewById(R.id.taskProgressBar)
        }

     //   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
      //      val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task_card, parent, false)
      //      return TaskViewHolder(view)
        }

       // override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
       //     val task = tasks[position]
        //    val context = holder.itemView.context

          //  holder.serviceType.text = task.serviceType
         //   holder.studentName.text = task.studentName
         //   holder.progressBar.progress = task.progressPercent
         //   holder.urgencyTag.text = task.urgency.name

         //   val tagColorResId = when(task.urgency){
          //      Urgency.URGENT -> R.color.red_urgent
          //      Urgency.MEDIUM -> R.color.orange_medium
           //     Urgency.LOW -> R.color.green_low
             //   else -> R.color.dark_shadow
         //   }

        //    val color = ContextCompat.getColor(context, tagColorResId)
       //     holder.urgencyTag.setBackgroundColor(color)
     //   }

//        fun updateList(newList: List<Task>){
  //          this.tasks = newList
    //        notifyDataSetChanged()
     //   }
    // }
//}