import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.math.BigDecimal;
import java.math.MathContext;

import javax.swing.Timer;
import javax.swing.event.MouseInputListener;



public class SftComponent extends Component implements MouseInputListener, Runnable, ActionListener, IPaletteChangeNotify
{
	private static final long serialVersionUID = 0;//get rid of warning
	BufferedImage mImage;
	BigDecimal mPos,mPosi;
	BigDecimal mSize;
	int mMax_iterations;
	int mResolution_x;
	int mResolution_y;
	SFTGui mGui;
	Timer mTimer;
	boolean mProcessing;
	IndexBuffer2D mBuffer;
	IndexBuffer2D mExport_buffer;
	IPalette mPalette;
	CalculationManager mCalculation;
	SuperSampleType mSuper_sample_type;
	int mNum_threads;
	long mStart_time;
	boolean mPressed;
	int mPressed_x;
	int mPressed_y;
	int mSelected_x;
	int mSelected_y;
	int mDragged_size;
	
	public SftComponent(SFTGui aGui)
	{
		mGui = aGui;
	}
	public void CreateImage()
	{
		mResolution_x = 1024;
		mResolution_y = 768;
		mSize = new BigDecimal(3.0);
		mPos = new BigDecimal(-0.75,MathContext.DECIMAL128);
		mPosi= new BigDecimal(0,MathContext.DECIMAL128);
		mMax_iterations = 1024;
		
		mGui.SetCoords(mSize,mPos,mPosi,mMax_iterations);
		
        mImage = new BufferedImage(mResolution_x, mResolution_y, BufferedImage.TYPE_INT_ARGB);
        
        UpdateImage();
	}
	
	void SetPalette( IPalette aPalette)
	{
		mPalette = aPalette;
	}
	
	void SetSuperSampleType(SuperSampleType aType)
	{
		mSuper_sample_type = aType;
	}
	void SetNumThreads(int aNumber)
	{
		mNum_threads = aNumber;
		if (mNum_threads<1)
			mNum_threads=1;
		if (mNum_threads>1024)
			mNum_threads=1024;
	}	
	
	@Override
	public void PaletteChanged()
	{
		mImage = mBuffer.MakeTexture(mPalette, mSuper_sample_type);
		repaint();
	}
	
	public BufferedImage GetImage()
	{
		return mImage;
	}
	
	public void run()
	{
		UpdateImage();
		mProcessing=false;
	}
	
	void Refresh()
	{
		if (mCalculation!=null)
		{
			if (mMax_iterations != mGui.GetIterations())
				mMax_iterations = mGui.GetIterations();
			else
			{
				mMax_iterations= Math.max(mMax_iterations, mCalculation.GetNewLimit());
				mGui.SetIterations(mMax_iterations);
			}
		}
		DoCalculation();
	}
	
	void DoCalculation()
	{
		mBuffer = DoCalculation(mResolution_x, mResolution_y, mSuper_sample_type);
	}

	void ExportCalculation(int aResolution_x, int aResolution_y, SuperSampleType aSuper_sample)
	{
		mExport_buffer = DoCalculation(aResolution_x, aResolution_y, aSuper_sample);
	}
	
	IndexBuffer2D DoCalculation(int aResolution_x, int aResolution_y, SuperSampleType aSuper_sample)
	{
		BigDecimal coords[] = new BigDecimal[2];
		
		mGui.StartProcessing();
		mStart_time = System.currentTimeMillis();
		mGui.SetCalculationTime( -1);
		coords = mGui.GetCoords();
		mMax_iterations = mGui.GetIterations();
		mSize = mGui.GetTheSize();
		mPos = coords[0];
		mPosi = coords[1];	
		int scale = mGui.GetTheSize().scale();
		int precision = mGui.GetTheSize().precision();
		int expo=0;
		precision = scale -precision + 8;
		
		IndexBuffer2D buffer=null;
		
		switch (aSuper_sample)
		{
		case SUPER_SAMPLE_NONE:
			buffer = new IndexBuffer2D(aResolution_x,aResolution_y);
			break;
		case SUPER_SAMPLE_2X:
			buffer = new IndexBuffer2D(aResolution_x+1,aResolution_y*2+1);
			break;
		case SUPER_SAMPLE_4X:
			buffer = new IndexBuffer2D(aResolution_x*2,aResolution_y*2);
			break;
		case SUPER_SAMPLE_4X_9:
			buffer = new IndexBuffer2D(aResolution_x*2+1,aResolution_y*2+1);
			break;
		case SUPER_SAMPLE_9X:
			buffer = new IndexBuffer2D(aResolution_x*3,aResolution_y*3);
			break;
		
		}
	
		CalculationManager calc = new CalculationManager();
		mCalculation = calc;
		
		double size;
		BigDecimal bd280 = new BigDecimal(1e-280);
		if (mSize.compareTo( bd280)<0)
		{
			BigDecimal mod_size = mSize;
			while (mod_size.compareTo( bd280)<0)
			{
				mod_size=mod_size.movePointRight(1);
				expo+=1;
			}
			size = mod_size.doubleValue();
				
		}
		else
		{
			size = mSize.doubleValue();
		}
		
		calc.SetCoordinates(mPos,mPosi,(size/2*mResolution_x)/mResolution_y,expo, new MathContext(precision));
		calc.SetBuffer(buffer, aSuper_sample);
		calc.SetIterationLimit(mMax_iterations);
		calc.SetAccuracy(1);
		calc.ThreadedCalculation(mNum_threads);
		
		if (mTimer==null)
		{
			mTimer = new Timer(100, this);
			mTimer.setInitialDelay(100);
		}
		mTimer.start(); 
		return buffer;
	}
	
	void UpdateImage()
	{ 		
		BigDecimal coords[] = new BigDecimal[2];
		
		coords = mGui.GetCoords();
		mMax_iterations = mGui.GetIterations();
		mSize = mGui.GetTheSize();
		mPos = coords[0];
		mPosi = coords[1];
		
		BigDecimal bigx  = mPos;
		BigDecimal bigy  = mPosi;
		
		int scale = mGui.GetTheSize().scale();
		int precision = mGui.GetTheSize().precision();
		int expo=0;
		
		precision = scale -precision + 8;
		
		mSuper_sample_type = SuperSampleType.SUPER_SAMPLE_2X;
		
		switch (mSuper_sample_type)
		{
		case SUPER_SAMPLE_NONE:
			mBuffer = new IndexBuffer2D(mResolution_x,mResolution_y);
			break;
		case SUPER_SAMPLE_2X:
			mBuffer = new IndexBuffer2D(mResolution_x+1,mResolution_y*2+1);
			break;
		case SUPER_SAMPLE_4X:
			mBuffer = new IndexBuffer2D(mResolution_x*2,mResolution_y*2);
			break;
		case SUPER_SAMPLE_4X_9:
			mBuffer = new IndexBuffer2D(mResolution_x*2+1,mResolution_y*2+1);
			break;
		case SUPER_SAMPLE_9X:
			mBuffer = new IndexBuffer2D(mResolution_x*3,mResolution_y*3);
			break;
		
		}

		double size;
		BigDecimal bd280 = new BigDecimal(1e-280);
		if (mSize.compareTo( bd280)<0)
		{
			BigDecimal mod_size = mSize;
			while (mod_size.compareTo( bd280)<0)
			{
				mod_size=mod_size.movePointRight(1);
				expo+=1;
			}
			size = mod_size.doubleValue();
				
		}
		else
		{
			size = mSize.doubleValue();
		}
		
		CalculationManager calc = new CalculationManager();
		calc.SetCoordinates(bigx,bigy,(size/2*mResolution_x)/mResolution_y,expo, new MathContext(precision));
		calc.SetBuffer(mBuffer, mSuper_sample_type);
		calc.SetIterationLimit(mMax_iterations);
		calc.SetAccuracy(1);
		calc.InitialiseCalculation();
		calc.CalculateSector(0,1,1);
		mGui.AddToUndoBuffer();
		
        mImage = mBuffer.MakeTexture(mPalette, mSuper_sample_type);
	}
	
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        
        g2.drawImage(mImage,0,0,null);
        g2.setColor (Color.gray);
        
        if (mDragged_size>0)
        {
        	int width = mDragged_size;
        	int height = (mDragged_size * 768)/1024;
          	g2.draw3DRect(mPressed_x-width/2, mPressed_y - height/2, width, height,true);
            
        }
    }
    
    public Dimension getPreferredSize(){
        return new Dimension(1024, 768);
    }
    
    
	@Override
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub

		if (mCalculation!=null && mCalculation.GetIsProcessing())
			return;
		if (mTimer!=null && mTimer.isRunning())
			return;
		
		int x = arg0.getX()-getX();
		int y = arg0.getY()-getY();
		
		if (arg0.getClickCount()==2)
		{
			mMax_iterations = mGui.GetIterations();
			if (mCalculation!=null)
			{
				mMax_iterations= Math.max(mMax_iterations, mCalculation.GetNewLimit());
				mGui.SetIterations(mMax_iterations);
			}
			
			double x_mul = x*1.0/mResolution_y - mResolution_x * 0.5/mResolution_y;
			double y_mul = (0.5*mResolution_y - y)/mResolution_y;
			
			BigDecimal x_offset = mSize.multiply(new BigDecimal(x_mul));
			BigDecimal y_offset = mSize.multiply(new BigDecimal(y_mul));
			
			int size_scale = mSize.scale();
			if (x_offset.scale() > size_scale+4)
				x_offset = x_offset.setScale( size_scale+4, BigDecimal.ROUND_HALF_DOWN);
			if (y_offset.scale() > size_scale+4)
				y_offset = y_offset.setScale( size_scale+4, BigDecimal.ROUND_HALF_DOWN);
			
			mPos = mPos.add( x_offset );
			mPosi = mPosi.add( y_offset );
			mSize = mSize.multiply(new BigDecimal(0.2));
			
			mPos = mPos.stripTrailingZeros();
			mPosi = mPosi.stripTrailingZeros();
			mSize = mSize.stripTrailingZeros();
			
			//mPos = mPos.add( new BigDecimal((x) * mSize/mResolution_y - mSize*mResolution_x/mResolution_y/2) );
			//mPosi = mPosi.add( new BigDecimal((mResolution_y/2-y) * mSize/mResolution_y));
			//mSize *= 0.2;
			
			//mSize_box.setText(Double.toString(mSize));
			mGui.SetCoords(mSize,mPos,mPosi, mMax_iterations);
			
			mGui.AddToUndoBuffer();
			DoCalculation();
			repaint();
		}
		else
		{
			if (mDragged_size>0)
			{
				int s = mDragged_size/2;
				if (x - mSelected_x <= s && mSelected_x -x <= s)
				{
					s = (mDragged_size*768)/1024/2;
					if (y - mSelected_y < s && mSelected_y-y < s)
					{
						double x_mul = mSelected_x*1.0/mResolution_y - mResolution_x * 0.5/mResolution_y;
						double y_mul = (0.5*mResolution_y - mSelected_y)/mResolution_y;
						
						BigDecimal x_offset = mSize.multiply(new BigDecimal(x_mul));
						BigDecimal y_offset = mSize.multiply(new BigDecimal(y_mul));
						
						int size_scale = mSize.scale();
						if (x_offset.scale() > size_scale+4)
							x_offset = x_offset.setScale( size_scale+4, BigDecimal.ROUND_HALF_DOWN);
						if (y_offset.scale() > size_scale+4)
							y_offset = y_offset.setScale( size_scale+4, BigDecimal.ROUND_HALF_DOWN);

						mPos = mPos.add( x_offset);
						mPosi = mPosi.add( y_offset);
						mSize = mSize.multiply(new BigDecimal(mDragged_size/1024.0));

						mPos = mPos.stripTrailingZeros();
						mPosi = mPosi.stripTrailingZeros();
						mSize = mSize.stripTrailingZeros();
						
						//mPos = mPos.add( new BigDecimal((mSelected_x) * mSize/mResolution_y - mSize*mResolution_x/mResolution_y/2) );
						//mPosi = mPosi.add( new BigDecimal((mResolution_y/2-mSelected_y) * mSize/mResolution_y));
						//mSize *= mDragged_size/1024.0;
						mGui.SetCoords(mSize,mPos,mPosi, mMax_iterations);
						mGui.AddToUndoBuffer();
						DoCalculation();
						repaint();
					}
				}
			}
		}
		if (mDragged_size!=0)
		{
			mDragged_size = 0;
			repaint();
		}
	}
	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void mousePressed(MouseEvent arg0)
	{
		mPressed = true;
		mPressed_x = arg0.getX()-getX();
		mPressed_y = arg0.getY()-getY();
		
	}
	@Override
	public void mouseReleased(MouseEvent arg0)
	{
		mPressed = false;		
	}
	@Override
	public void mouseDragged(MouseEvent arg0)
	{
		int x = arg0.getX()-getX();
		int y = arg0.getY()-getY();
		
		mDragged_size =2*Math.abs(x-mPressed_x);
		int ds2 = (Math.abs(y-mPressed_y)*2*1024)/768;
		mDragged_size = Math.max(mDragged_size, ds2);
		repaint();
		mSelected_x = mPressed_x;
		mSelected_y = mPressed_y;
	}
	@Override
	public void mouseMoved(MouseEvent arg0)
	{
		if (mCalculation!=null && mCalculation.GetIsProcessing())
			return;
		
		int x = arg0.getX()-getX();
		int y = mResolution_y - arg0.getY()-getY();
		
		if (x<0 || x>=mResolution_x || y<0 || y>=mResolution_y)
			return;
		
		if (mSuper_sample_type == SuperSampleType.SUPER_SAMPLE_4X)
		{
			x*=2;
			y*=2;
		}
		else if (mSuper_sample_type == SuperSampleType.SUPER_SAMPLE_4X_9)
		{
			x=x*2+1;
			y=y*2+1;
		}
		else if (mSuper_sample_type == SuperSampleType.SUPER_SAMPLE_9X)
		{
			x=x*3+1;
			y=y*3+1;
		}
		
		if (mBuffer!=null && y<mBuffer.GetHeight() && x<mBuffer.GetWidth())
		{
			int index = mBuffer.GetValue(x,y);
			mGui.SetHoverIndex(index);
		}		
	}
	@Override
	public void actionPerformed(ActionEvent arg0)
	{
		if (!mCalculation.GetIsProcessing())
		{
			if (mExport_buffer!=null)
			{
		        BufferedImage image = mExport_buffer.MakeTexture(mPalette, mCalculation.GetSuperSampleType());
		        if (image==null)
		        {
			        mExport_buffer = null;
		        	mGui.OutOfMemory();
		        }
		        else
		        {
			        mExport_buffer = null;
					mGui.ExportImage(image);
		        }
			}
			else
			{
		        mImage = mBuffer.MakeTexture(mPalette, mSuper_sample_type);
				repaint();
				//mMax_iterations = mCalculation.GetNewLimit();
			}
			mTimer.stop();
			mGui.EndProcessing();
			mGui.SetCalculationTime( System.currentTimeMillis() - mStart_time);
		}
		else
		{
			if (mExport_buffer!=null)
				mGui.SetProgress(mCalculation.GetProgress(), mExport_buffer.GetWidth()* mExport_buffer.GetHeight());
			else
				mGui.SetProgress(mCalculation.GetProgress(), mBuffer.GetWidth()* mBuffer.GetHeight());
		}
	}
	
	void Cancel()
	{
		if (mCalculation!=null)
		{
			mCalculation.Cancel();
		}
	}
}