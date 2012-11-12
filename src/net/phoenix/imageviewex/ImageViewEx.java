package net.phoenix.imageviewex;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import java.io.InputStream;

/**
 * Extension of the ImageView that handles any kind of image already supported
 * by ImageView, plus animated GIF images.
 * 
 * @author Sebastiano Poggi
 * @author Francesco Pontillo
 * 
 */
public class ImageViewEx extends ImageView {

    private static final String TAG = ImageViewEx.class.getSimpleName();
	
    private static boolean mCanAlwaysAnimate = true;
    private float mScale = -1;

    private static final int IMAGE_SOURCE_UNKNOWN = -1;
    private static final int IMAGE_SOURCE_RESOURCE = 0;
    private static final int IMAGE_SOURCE_DRAWABLE = 1;
    private static final int IMAGE_SOURCE_BITMAP = 2;
    private static final int IMAGE_SOURCE_GIF = 2;

    private int mImageSource;

	// Used by the fixed size optimizations
	private boolean mIsFixedSize = false;
	private boolean mBlockLayout = false;
	
    private BitmapFactory.Options mOptions;
    private int mOverriddenDensity = -1;
    private static int mOverriddenClassDensity = -1;

    private Movie mGif;
    private double mGifStartTime;
    private int mFrameDuration = 67;
    private Handler mHandler = new Handler();
    private Thread mUpdater;

    ///////////////////////////////////////////////////////////
    ///                  CONSTRUCTORS                       ///
    ///////////////////////////////////////////////////////////

    /**
     * Creates an instance for the class.
     *
     * @param context The context to instantiate the object for.
     */
    public ImageViewEx(Context context) {
        super(context);
    }

    /**
     * Creates an instance for the class and initializes it with a given image.
     *
     * @param context	The context to initialize the instance into.
     * @param src		InputStream containing the GIF to view.
     */
    public ImageViewEx(Context context, InputStream src) {
        super(context);
        mGif = Movie.decodeStream(src);
    }

    /**
     * Creates an instance for the class.
     *
     * @param context	The context to initialize the instance into.
     * @param attrs		The parameters to initialize the instance with.
     */
    public ImageViewEx(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Creates an instance for the class and initializes it with a provided GIF.
     *
     * @param context	The context to initialize the instance into.
     * @param src		The byte array containing the GIF to view.
     */
    public ImageViewEx(Context context, byte[] src) {
        super(context);
        mGif = Movie.decodeByteArray(src, 0, src.length);
    }

    /**
     * Creates an instance for the class and initializes it with a provided GIF.
     *
     * @param context Il contesto in cui viene inizializzata l'istanza.
     * @param src     The path of the GIF file to view.
     */
    public ImageViewEx(Context context, String src) {
        super(context);
        mGif = Movie.decodeFile(src);
    }
    
    /**
     * Sets a density for every image set to any {@link ImageViewEx}.
     * If a custom density level is set for a particular instance of {@link ImageViewEx},
     * this will be ignored.
     * 
     * @param classLevelDensity the density to apply to every instance of {@link ImageViewEx}.
     */
    public static void setClassLevelDensity(int classLevelDensity) {
    	mOverriddenClassDensity = classLevelDensity;
    }
    
    /**
     * Removes the class level density for {@link ImageViewEx}.
     * 
     * @see ImageViewEx#setClassLevelDensity(int)
     */
    public static void removeClassLevelDensity() {
    	setClassLevelDensity(-1);
    }
    
    /**
     * Checks whether the class level density has been set.
     * 
     * @see ImageViewEx#setClassLevelDensity(int)
     * 
     * @return true if it has been set, false otherwise.
     */
    public static boolean isClassLevelDensitySet() {
    	return (mOverriddenClassDensity != -1);
    }
    
    /**
     * Gets the class level density has been set.
     * 
     * @see ImageViewEx#setClassLevelDensity(int)
     * 
     * @return int, the class level density
     */
    public static int getClassLevelDensity() {
    	return mOverriddenClassDensity;
    }
    
    /**
     * Sets the view's density to the one from the screen.
     * The new density will be set on the next {@link #onMeasure(int, int)}.
     * 
     * @return the new density the view has to use.
     */
    public void dontOverrideDensity() {
    	mOverriddenDensity = -1;
    }
    
    /**
     * Programmatically overrides this view's density.
     * The new density will be set on the next {@link #onMeasure(int, int)}.
     * 
     * @param fixedDensity the new density the view has to use.
     */
    public void setDensity(int fixedDensity) {
    	mOverriddenDensity = fixedDensity;
    }

    /**
     * Gets the set density of the view, given by the screen density or by value
     * overridden with {@link #setDensity(int)}.
     * If the density was not overridden and it can't be retrieved by the context,
     * it simply returns the DENSITY_HIGH constant.
     * 
     * @return int representing the current set density of the view.
     */
    public int getDensity() {
        int density;
        
        // If a custom instance density was set, set the image to this density
        if (mOverriddenDensity > 0) {
        	density = mOverriddenDensity;
        } else if (isClassLevelDensitySet()) {
        	// If a class level density has been set, set every image to that density
        	density = getClassLevelDensity();
        } else {
        	 // If the instance density was not overridden, get the one from the display
	        DisplayMetrics metrics = new DisplayMetrics();
	
	        if (!(getContext() instanceof Activity)) {
	        	density = DisplayMetrics.DENSITY_HIGH;
	        } else {
	            Activity activity = (Activity) getContext();
	            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
	            density = metrics.densityDpi;
	        }
        }
        
        return density;
    }
    
    /**
     * Class method.
     * Sets the mCanAlwaysAnimate value. If it is true, {@link #canAnimate()} will be 
     * triggered, determining if the animation can be played in that particular instance of
     * {@link ImageViewEx}. If it is false, {@link #canAnimate()} will never be triggered
     * and GIF animations will never start.
     * {@link mCanAlwaysAnimate} defaults to true.
     * 
     * @param mCanAlwaysAnimate 	boolean, true to always animate for every instance of
     * 							{@link ImageViewEx}, false if you want to perform the
     * 							decision method {@link #canAnimate()} on every
     * 							{@link #setSource(byte[])} call.
     */
    public static void setCanAlwaysAnimate(boolean mCanAlwaysAnimate) {
    	ImageViewEx.mCanAlwaysAnimate = mCanAlwaysAnimate;
    }
    
    /**
     * Class method.
     * Returns the mCanAlwaysAnimate value. If it is true, {@link #canAnimate()} will be 
     * triggered, determining if the animation can be played in that particular instance of
     * {@link ImageViewEx}. If it is false, {@link #canAnimate()} will never be triggered
     * and GIF animations will never start.
     * {@link mCanAlwaysAnimate} defaults to true.
     * 
     * @return 	boolean, true to see if this instance can be animated by calling
     * 			{@link #canAnimate()}, if false, animations will never be triggered and
     * 			{@link #canAnimate()} will never be evaluated for this instance.
     */
    public static boolean getCanAlwaysAnimate() {
    	return mCanAlwaysAnimate;
    }
	
	/**
	 * Sets a value indicating wether the image is considered as having a fixed size.
	 * This will enable an optimization when assigning images to the ImageViewEx, but
	 * has to be used sparingly or it may cause artifacts if the image isn't really
	 * fixed in size.
	 * <p />
	 * An example of usage for this optimization is in ListViews, where items images
	 * are supposed to be fixed size, and this enables buttery smoothness.
	 * <p />
	 * See: https://plus.google.com/u/0/113058165720861374515/posts/iTk4PjgeAWX
	 */
	public void setIsFixedSize(boolean fixedSize) {
		mIsFixedSize = fixedSize;
	}
	
	/**
	 * Sets a value indicating wether the image is considered as having a fixed size.
	 * See {@link #setIsFixedSize(boolean)} for further details.
	 */
	public boolean getIsFixedSize() {
		return mIsFixedSize;
	}
    
    /**
     * This method should be overridden with your custom implementation. By default,
     * it always returns {@code <code>true</code>}.
     * 
     * <p>This method decides whether animations can be started for this instance of
     * {@link ImageViewEx}. Still, if {@link #getCanAlwaysAnimate()} equals
     * {@code <code>false</code>} this method will never be called for all of the
     * instances of {@link ImageViewEx}.
     * 
     * @see {@link #setCanAlwaysAnimate(boolean)} to set the predefined class behavior
     * in regards to animations.
     * 
     * @return 	{@code <code>true</code>} if it can animate the current instance of
     * 			{@link ImageViewEx}, false otherwise.
     */
    public boolean canAnimate() {
    	return true;
    }
    
    public float getScale() {
    	float targetDensity = getContext().getResources().getDisplayMetrics().densityDpi;
    	float displayThisDensity = getDensity();
    	mScale = targetDensity / displayThisDensity;
    	if (mScale < 0.1f) mScale = 0.1f;
        if (mScale > 5.0f) mScale = 5.0f;
        return mScale;
    }

    /**
     * Assign an Options object to this {@link ImageViewEx}. Those options
     * are used internally by the {@link ImageViewEx} when decoding the
     * image. This may be used to prevent the default behavior that loads all
     * images as mdpi density.
     *
     * @param options The BitmapFactory.Options used to decode the images.
     */
    public void setOptions(BitmapFactory.Options options) {
        mOptions = options;
    }

    static class SavedState extends BaseSavedState {
        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
    }

    ///////////////////////////////////////////////////////////
    ///                 PUBLIC SETTERS                      ///
    ///////////////////////////////////////////////////////////

    /**
     * Initalizes the inner variable describing the kind of resource attached to the ImageViewEx.
     */
    public void initializeDefaultValues() {
        if (isPlaying()) stop();
        mGif = null;
        setTag(null);
        mImageSource = IMAGE_SOURCE_UNKNOWN;
    }
    
    /**
     * Sets the image from a byte array.
     *
     * @param src The byte array containing the image to set into the ImageViewEx.
     */
    public void setSource(byte[] src) {
    	Movie mGif;
    	mGif = Movie.decodeByteArray(src, 0, src.length);
        // If mGif is null, it's probably not a mGif
        if (mGif == null || !(internalCanAnimate())) {
            // If not a mGif and if on Android 3+, enable HW acceleration
            if (Build.VERSION.SDK_INT >= 11)
                setLayerType(View.LAYER_TYPE_HARDWARE, null);
            // Sets the image as a regular Drawable
            setTag(null);
            
            Drawable d = Converters.byteArrayToDrawable(src, mOptions, getContext());
            this.setImageDrawable(d);
            this.measure(0, 0);
            this.requestLayout();

            try {
                AnimationDrawable animationDrawable = (AnimationDrawable) this.getDrawable();
                animationDrawable.start();
            } catch (Exception ignored) { }
        } else {
            this.measure(0, 0);
            this.requestLayout();

            // Disables the HW acceleration when viewing a GIF on Android 3+
            if (Build.VERSION.SDK_INT >= 11)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            
            initializeDefaultValues();
            mImageSource = IMAGE_SOURCE_GIF;
            mGif = mGif;
            play();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setImageResource(int resId) {
        initializeDefaultValues();
    	super.setImageResource(resId);
    	mImageSource = IMAGE_SOURCE_RESOURCE;
    }

    /**
     * {@inheritDoc}
     */
    public void setImageDrawable(Drawable drawable) {
		blockLayoutIfPossible();
        initializeDefaultValues();
        super.setImageDrawable(drawable);
        mBlockLayout = false;
    	mImageSource = IMAGE_SOURCE_DRAWABLE;
    }
    
    /**
     * {@inheritDoc}
     */
    public void setImageBitmap(Bitmap bm) {
        initializeDefaultValues();
        super.setImageBitmap(bm);
    	mImageSource = IMAGE_SOURCE_BITMAP;
    }

    /**
     * Sets the duration, in milliseconds, of each frame during the GIF animation.
     * It is the refresh period.
     *
     * @param duration The duration, in milliseconds, of each frame.
     */
    public void setFramesDuration(int duration) {
        if (duration < 1)
            throw new IllegalArgumentException
            	("Frame duration can't be less or equal than zero.");

        mFrameDuration = duration;
    }

    /**
     * Sets the number of frames per second during the GIF animation.
     *
     * @param fps The fps amount.
     */
    public void setFPS(float fps) {
        if (fps <= 0.0)
            throw new IllegalArgumentException
            	("FPS can't be less or equal than zero.");

        mFrameDuration = Math.round(1000f / fps);
    }

    ///////////////////////////////////////////////////////////
    ///                 PUBLIC GETTERS                      ///
    ///////////////////////////////////////////////////////////

    /**
     * Returns a boolean indicating if an animation is currently playing.
     * 
     * @return true if animating, false otherwise.
     */
    public boolean isPlaying() {
        return mUpdater != null && mUpdater.isAlive();
    }

    /**
     * Returns a boolean indicating if the instance was initialized and if
     * it is ready for playing the animation.
     *
     * @return true if the instance is ready for playing, false otherwise.
     */
    public boolean canPlay() {
        return mGif != null;
    }

    /**
     * Gets the frame duration, in milliseconds, of each frame during the GIF animation.
     * It is the refresh period.
     *
     * @return The duration, in milliseconds, of each frame.
     */
    public int getFramesDuration() {
        return mFrameDuration;
    }

    /**
     * Gets the number of frames per second during the GIF animation.
     *
     * @return The fps amount.
     */
    public float getFPS() {
        return 1000.0f / mFrameDuration;
    }

    ///////////////////////////////////////////////////////////
    ///                   PUBLIC METHODS                    ///
    ///////////////////////////////////////////////////////////

    /**
     * Starts playing the GIF, if it hasn't started yet.
     * FPS defaults to 15..
     */
    public void play() {
        // Do something if the animation hasn't started yet
        if (mUpdater == null || !mUpdater.isAlive()) {
            // Check id the animation is ready
            if (!canPlay()) {
                throw new IllegalStateException
                	("Animation can't start before a GIF is loaded.");
            }

            // Initialize the thread and start it
            mUpdater = new Thread() {

                @Override
                public void run() {

                    // Infinite loop: invalidates the View.
                    // Stopped when the thread is stopped or interrupted.
                    while (mUpdater != null && !mUpdater.isInterrupted()) {

                        mHandler.post(new Runnable() {
                            public void run() {
                                ImageViewEx.this.invalidate();
                            }
                        });

                        // The thread sleeps until the next frame
                        try {
                            Thread.sleep(mFrameDuration);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            };

            mUpdater.start();
        }
    }

    /**
     * Pause playing the GIF, if it has started.
     */
    public void pause() {
        // If the animation has started
        if (mUpdater != null && mUpdater.isAlive()) {
            mUpdater.suspend();
        }
    }

    /**
     * Stops playing the GIF, if it has started.
     */
    public void stop() {
    	// If the animation has started
        if (mUpdater != null && mUpdater.isAlive() && canPlay()) {
            mUpdater.interrupt();
            mGifStartTime = 0;
        }
    }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
    public void requestLayout() {
        if (!mBlockLayout) {
            super.requestLayout();
        }
    }

    ///////////////////////////////////////////////////////////
    ///                  EVENT HANDLERS                     ///
    ///////////////////////////////////////////////////////////

    /**
     * Draws the control
     *
     * @param canvas The canvas to drow onto.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        if (mGif != null) {
            long now = android.os.SystemClock.uptimeMillis();
            
            // first time	
            if (mGifStartTime == 0) {
                mGifStartTime = now;
            }

            int dur = mGif.duration();
            if (dur == 0) {
                dur = 1000;
            }
            int relTime = (int) ((now - mGifStartTime) % dur);
            mGif.setTime(relTime);
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.scale(mScale, mScale);
            mGif.draw(canvas, this.getWidth() - (mGif.width() * mScale), this.getHeight() - (mGif.height() * mScale));
            canvas.restore();
        } else {
            super.onDraw(canvas);
        }
    }

    /**
     * @see android.view.View#measure(int, int)
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mScale = getScale();
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
    }

    ///////////////////////////////////////////////////////////
    ///                  PRIVATE HELPERS                    ///
    ///////////////////////////////////////////////////////////

    /**
     * Determines the width of this View
     *
     * @param measureSpec A measureSpec packed into an int
     * @return The width of the view, honoring constraints from measureSpec
     */
    private int measureWidth(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            // We were told how big to be
            result = specSize;
        } else {
            // Measure the mGif
            if (mGif != null)
                result = Math.round(mGif.width() * mScale) + getPaddingLeft() + getPaddingRight();
            else        // Measure the drawable
                result = Math.round(getDrawable().getIntrinsicWidth() * mScale) + getPaddingLeft() + getPaddingRight();

            if (specMode == MeasureSpec.AT_MOST) {
                // Respect AT_MOST value if that was what is called for by measureSpec
                result = Math.min(result, specSize);
            }
        }

        return result;
    }

    /**
     * Determines the height of this view
     *
     * @param measureSpec A measureSpec packed into an int
     * @return The height of the view, honoring constraints from measureSpec
     */
    private int measureHeight(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            // We were told how big to be
            result = specSize;
        } else {

            // Measure the mGif
            if (mGif != null)
                result = Math.round(mGif.height() * mScale) + getPaddingTop() + getPaddingBottom();
            else        // Measure the drawable
                result = Math.round(getDrawable().getIntrinsicHeight() * mScale) + getPaddingTop() + getPaddingBottom();

            if (specMode == MeasureSpec.AT_MOST) {
                // Respect AT_MOST value if that was what is called for by measureSpec
                result = Math.min(result, specSize);
            }
        }

        return result;
    }
    
	/**
	 * Blocks layout recalculation if the image is set as fixed size
	 * to prevent unnecessary calculations and provide butteriness.
	 */
	private void blockLayoutIfPossible() {
        if (mIsFixedSize) {
            mBlockLayout = true;
        }
    }
    
    /**
     * Internal method, deciding whether to trigger the custom decision method {@link #canAnimate()}
     * or to use the static class value of mCanAlwaysAnimate.
     * 
     * @return true if the animation can be started, false otherwise.
     */
    private boolean internalCanAnimate() {
    	if (getCanAlwaysAnimate()) return canAnimate();
    	else return getCanAlwaysAnimate();
    }
	
}
