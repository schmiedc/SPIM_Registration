package spim.process.fusion.export;

import ij.ImagePlus;
import ij.gui.GenericDialog;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;

public class DisplayImage implements ImgExport
{
	final boolean virtualDisplay;
	
	public DisplayImage() { this( false ); }
	public DisplayImage( final boolean virtualDisplay ) { this.virtualDisplay = virtualDisplay; }

	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final String title )
	{
		exportImage( img, null, title );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval< T > img, final BoundingBox bb, final String title )
	{
		return exportImage( img, bb, title, Double.NaN, Double.NaN );
	}

	@SuppressWarnings("unchecked")
	public <T extends RealType<T> & NativeType<T>> boolean exportImage( final RandomAccessibleInterval<T> img, final BoundingBox bb, final String title, final double min, final double max )
	{
		// do nothing in case the image is null
		if ( img == null )
			return false;
		
		// determine min and max
		final float[] minmax;
		
		if ( Double.isNaN( min ) || Double.isNaN( max ) )
			minmax = FusionHelper.minMax( img );
		else
			minmax = new float[]{ (float)min, (float)max };

		ImagePlus imp = null;
		
		if ( img instanceof ImagePlusImg )
			try { imp = ((ImagePlusImg<T, ?>)img).getImagePlus(); } catch (ImgLibException e) {}

		if ( imp == null )
		{
			if ( virtualDisplay )
				imp = ImageJFunctions.wrap( img, title );
			else
				imp = ImageJFunctions.wrap( img, title ).duplicate();
		}

		imp.setTitle( title );

		if ( bb != null )
		{
			imp.getCalibration().xOrigin = -(bb.min( 0 ) / bb.getDownSampling());
			imp.getCalibration().yOrigin = -(bb.min( 1 ) / bb.getDownSampling());
			imp.getCalibration().zOrigin = -(bb.min( 2 ) / bb.getDownSampling());
			imp.getCalibration().pixelWidth = imp.getCalibration().pixelHeight = imp.getCalibration().pixelDepth = bb.getDownSampling();
		}
		
		imp.setDimensions( 1, (int)img.dimension( 2 ), 1 );
		
		imp.setDisplayRange( minmax[ 0 ], minmax[ 1 ] );
		
		imp.updateAndDraw();
		imp.show();

		return true;
	}

	@Override
	public boolean queryParameters( final SpimData2 spimData ) { return true; }

	@Override
	public void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) {}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) { return true; }

	@Override
	public ImgExport newInstance() { return new DisplayImage(); }

	@Override
	public String getDescription() { return "Display using ImageJ"; }
}