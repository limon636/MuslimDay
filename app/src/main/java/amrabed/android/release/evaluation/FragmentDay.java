package amrabed.android.release.evaluation;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import amrabed.android.release.evaluation.app.ApplicationEvaluation;
import amrabed.android.release.evaluation.db.DatabaseEntry;
import amrabed.android.release.evaluation.main.Selection;

public class FragmentDay extends ListFragment
{
	static final String ARGS = "args";

	DatabaseEntry e;
	List<String> itemList = new ArrayList<>();
	MyAdapter adapter;

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		registerForContextMenu(getListView());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		Bundle args = getArguments();
		if (args != null)
		{
			e = ApplicationEvaluation.getDatabase().getEntry(args.getLong(ARGS));
		}
		else
		{
			// Should never be called .. left for history reasons !
			e = ApplicationEvaluation.getDatabase().getEntry(new DateTime().withTimeAtStartOfDay().getMillis());
		}
		adapter = new MyAdapter(getActivity(), android.R.layout.simple_list_item_1, itemList);
		readItems();
		setListAdapter(adapter);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		getListView().scrollTo(0, getActivity().getPreferences(0).getInt("Position", 0));
	}

	@Override
	public void onPause()
	{
		super.onPause();
		getActivity().getPreferences(0).edit().putInt("Position", getListView().getScrollY()).apply();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		getActivity().getMenuInflater().inflate(R.menu.context, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		View view = info.targetView;
		int position = info.position;

		switch (item.getItemId())
		{
			case R.id.not_yet:
				respond(new Selection(Selection.Value.NA), position, view);
				break;
			case R.id.yes:
				respond(new Selection(Selection.Value.GOOD), position, view);
				break;
			case R.id.no_w:
				respond(new Selection(Selection.Value.OK), position, view);
				break;
			case R.id.no_wo:
				respond(new Selection(Selection.Value.BAD), position, view);
				break;
			default:
				return super.onContextItemSelected(item);
		}
		return true;

	}

	@Override
	public void onListItemClick(ListView l, View view, int position, long id)
	{
		respond(new Selection(e.getSelectionAt(position)).getNext(), position, view);
	}

	private void respond(Selection selection, int position, View view)
	{
		e.updateSelectionAt(position, selection.getValue());
		setIcon((TextView) view.findViewById(android.R.id.text1), selection.getIcon());
		ApplicationEvaluation.getDatabase().update(e.getDate(), e.getSelections());
		PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
				.putLong("LAST_UPDATE", Calendar.getInstance().getTimeInMillis()).apply();
	}

	void setIcon(TextView tv, int icon)
	{
		if (getResources().getConfiguration().locale.getDisplayName().toLowerCase().contains("english"))
		{
			tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0);
		}
		else
		{
			tv.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
		}
//		if(icon == 0)
//		{
//			tv.setActivated(false);
//		}
//		else
//		{
//			tv.setActivated(true);
//		}

	}

	private void readItems()
	{
		try
		{
			itemList.clear();
			FileInputStream in = getActivity().openFileInput(ActivityEdit.LIST_FILE);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (isIncluded(line))
				{
					itemList.add(line);
				}
			}
		}
		catch (FileNotFoundException x)
		{
			boolean isMale = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext()).getBoolean("gender", true);

			String items[] = getResources().getStringArray(isMale ? R.array.m_activities : R.array.f_activities);
			for (String item : items)
			{
				if (isIncluded(item))
				{
					itemList.add(item);
				}
			}
		}
		catch (Exception x)
		{
			Log.e(getClass().getName(), x.toString());
		}
		adapter.notifyDataSetChanged();
		ApplicationEvaluation.getDatabase().update(e.getDate(), (short) itemList.size());
		// PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().putLong("LAST_UPDATE",
		// Calendar.getInstance().getTimeInMillis()).commit();
	}

	private boolean isIncluded(String s)
	{
		boolean isFriday = (new LocalDate(e.getDate()).getDayOfWeek() == DateTimeConstants.FRIDAY);
		return !(((!e.isRecitingDay()) && (s.contains(getString(R.string.recite_q)))) ||
				((!e.isDietDay()) && (s.contains((getString(R.string.diet_q))))) ||
				((!e.isMemorizingDay()) && (s.contains((getString(R.string.memorize_q))))) ||
				((!e.isFastingDay()) && (s.contains((getString(R.string.fasting_q))))) ||
				((!isFriday) && (s.contains((getString(R.string.bath_q))))));
	}

	class MyAdapter extends ArrayAdapter<String>
	{
		MyAdapter(Context context, int layout, List<String> list)
		{
			super(context, layout, list);
		}

		@NonNull
		@Override
		public View getView(int position, View view, @NonNull ViewGroup parent)
		{
			TextView tv;
			String txt = itemList.get(position);
			if (view == null)
			{
				LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(android.R.layout.simple_list_item_activated_1, null);
				tv = (TextView) view.findViewById(android.R.id.text1);
				view.setTag(tv);
			}
			else
			{
				tv = (TextView) view.getTag();
				setIcon(tv, 0);
			}
			tv.setText(txt);
			setIcon(tv, Selection.Icon.list[e.getSelectionAt(position)]);
			return view;
		}
	}

}