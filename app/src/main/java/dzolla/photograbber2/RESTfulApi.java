package dzolla.photograbber2;

import dzolla.photograbber2.pojos.RESTresponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by eee on 11.05.2017.
 */

public interface RESTfulApi {

    @GET("place/nearbysearch/json?")
    Call<RESTresponse> getPlaces(@Query("key") String key, @Query("location") String coordinates, @Query("radius") String radius);

}

